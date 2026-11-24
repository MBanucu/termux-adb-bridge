package io.github.mbanucu.termux;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;
import java.util.Map;

/**
 * Builds and sends one Termux {@code RUN_COMMAND}.
 *
 * <pre>{@code
 * TermuxCommand.builder(context)
 *     .script("$PREFIX/bin/pin-adb-port.sh")
 *     .background(true)
 *     .label("pin")
 *     .send(result -> show(result.uiLine()));
 * }</pre>
 *
 * <p>The command is addressed to Termux's {@code RunCommandService} explicitly
 * and runs as Termux's own uid. The caller must hold
 * {@link TermuxContract#PERMISSION_RUN_COMMAND}, and Termux must have
 * {@code allow-external-apps=true} in its private properties file.
 * {@link TermuxChecks} checks installation and the permission; the property is
 * inside Termux's home and can only be explained, not read.
 *
 * <p>Without a callback this is fire-and-forget, like the original bridge. A
 * callback (or {@link #sendTo(Class)}) attaches a fresh one-shot pending
 * intent, so every command gets its own result slot.
 */
public final class TermuxCommand {

    private final Context context;
    private final TermuxIntentSpec spec;
    private final boolean foregroundService;

    private TermuxCommand(Context context, TermuxIntentSpec spec, boolean foregroundService) {
        this.context = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        this.spec = spec;
        this.foregroundService = foregroundService;
    }

    public static Builder builder(Context context) {
        if (context == null) throw new IllegalArgumentException("context == null");
        return new Builder(context);
    }

    /** The Android-free shape of this command. */
    public TermuxIntentSpec spec() {
        return spec;
    }

    /**
     * The Intent that will be sent. It carries no result pending intent; use
     * {@link #send(TermuxResultCallback)} for that.
     */
    public Intent build() {
        return build(null);
    }

    /**
     * Sends the command. Throws whatever Android throws when a background
     * service start is refused (for example
     * {@code ForegroundServiceStartNotAllowedException} on Android 12+);
     * use {@link #trySend()} to get a boolean instead.
     */
    public void send() {
        start(build(null));
    }

    /** As {@link #send()}, returning false instead of throwing. */
    public boolean trySend() {
        try {
            send();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Sends the command and delivers its result through
     * {@link TermuxResultService}. The callback is process-local and one-shot;
     * it is dropped if the process dies before Termux answers.
     */
    public void send(TermuxResultCallback callback) {
        if (callback == null) {
            send();
            return;
        }
        int requestCode = TermuxPendingIntents.nextRequestCode();
        Intent result = resultIntent(TermuxResultService.class, requestCode);
        TermuxResultDispatcher.register(requestCode, callback);
        try {
            start(build(TermuxPendingIntents.forService(context, result, requestCode)));
        } catch (RuntimeException e) {
            TermuxResultDispatcher.unregister(requestCode);
            throw e;
        }
    }

    /** As {@link #send(TermuxResultCallback)}, returning false instead of throwing. */
    public boolean trySend(TermuxResultCallback callback) {
        try {
            send(callback);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Sends the command and delivers its result to a broadcast receiver. The
     * receiver class must be declared in the host manifest; subclass
     * {@link TermuxResultReceiver} to get the parsed result.
     */
    public void sendTo(Class<? extends BroadcastReceiver> receiverClass) {
        if (receiverClass == null) throw new IllegalArgumentException("receiverClass == null");
        int requestCode = TermuxPendingIntents.nextRequestCode();
        Intent result = resultIntent(receiverClass, requestCode);
        start(build(TermuxPendingIntents.forBroadcast(context, result, requestCode)));
    }

    /** As {@link #sendTo(Class)}, returning false instead of throwing. */
    public boolean trySendTo(Class<? extends BroadcastReceiver> receiverClass) {
        try {
            sendTo(receiverClass);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Intent resultIntent(Class<?> target, int requestCode) {
        Intent result = new Intent(context, target);
        result.putExtra(TermuxResultService.EXTRA_SCRIPT, spec.script());
        result.putExtra(TermuxResultService.EXTRA_REQUEST_CODE, requestCode);
        return result;
    }

    private Intent build(android.app.PendingIntent pendingIntent) {
        Intent intent = new Intent();
        intent.setClassName(spec.componentPackage(), spec.componentClass());
        intent.setAction(spec.action());
        for (Map.Entry<String, Object> extra : spec.extras().entrySet()) {
            Object value = extra.getValue();
            if (value instanceof String) {
                intent.putExtra(extra.getKey(), (String) value);
            } else if (value instanceof String[]) {
                intent.putExtra(extra.getKey(), (String[]) value);
            } else if (value instanceof Boolean) {
                intent.putExtra(extra.getKey(), (Boolean) value);
            } else if (value instanceof Integer) {
                intent.putExtra(extra.getKey(), (Integer) value);
            } else {
                throw new IllegalStateException(
                        "unsupported extra type for " + extra.getKey() + ": " + value);
            }
        }
        if (pendingIntent != null) {
            intent.putExtra(TermuxIntentSpec.EXTRA_PENDING_INTENT, pendingIntent);
        }
        return intent;
    }

    private void start(Intent run) {
        // Termux's service is the target. A plain Activity-based caller can
        // use startService; a caller that may be in the background sets
        // foregroundService(true), which is what the bridge activity does and
        // what Android 8+ requires for a background start.
        if (foregroundService && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(run);
        } else {
            context.startService(run);
        }
    }

    /** Mutable builder; every value ends up in {@link TermuxIntentSpec}. */
    public static final class Builder {
        private final Context context;
        private final TermuxIntentSpec.Builder spec;
        private boolean foregroundService;

        private Builder(Context context) {
            this.context = context;
            this.spec = TermuxIntentSpec.builder();
        }

        public Builder script(String path) {
            spec.script(path);
            return this;
        }

        public Builder args(String... args) {
            spec.args(args);
            return this;
        }

        public Builder args(List<String> args) {
            spec.args(args);
            return this;
        }

        public Builder workdir(String path) {
            spec.workdir(path);
            return this;
        }

        public Builder background(boolean background) {
            spec.background(background);
            return this;
        }

        public Builder runner(String runner) {
            spec.runner(runner);
            return this;
        }

        public Builder label(String label) {
            spec.label(label);
            return this;
        }

        public Builder description(String description) {
            spec.description(description);
            return this;
        }

        public Builder sessionAction(int action) {
            spec.sessionAction(action);
            return this;
        }

        /**
         * Start Termux's service with {@code startForegroundService} on
         * Android 8+. Off by default: a visible Activity can use
         * {@code startService}, while a background caller (the adb bridge
         * activity) needs the foreground form.
         */
        public Builder foregroundService(boolean foregroundService) {
            this.foregroundService = foregroundService;
            return this;
        }

        /** The Android-free shape, without building or sending anything. */
        public TermuxIntentSpec spec() {
            return spec.build();
        }

        /** Creates the command; the builder may then be reused. */
        public TermuxCommand build() {
            return new TermuxCommand(context, spec.build(), foregroundService);
        }

        /** Builds and sends fire-and-forget. */
        public void send() {
            build().send();
        }

        /** Builds and sends with a one-shot service callback. */
        public void send(TermuxResultCallback callback) {
            build().send(callback);
        }

        /** Builds and sends with a broadcast receiver result adapter. */
        public void sendTo(Class<? extends BroadcastReceiver> receiverClass) {
            build().sendTo(receiverClass);
        }
    }
}
