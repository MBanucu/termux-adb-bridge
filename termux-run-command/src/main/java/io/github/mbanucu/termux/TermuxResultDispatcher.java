package io.github.mbanucu.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns an Intent delivered by {@link TermuxResultService} or a
 * {@link TermuxResultReceiver} into a {@link TermuxResult} and hands it to the
 * callback registered for that request code.
 *
 * <p>Callbacks are process-local and best-effort, exactly like the one-shot
 * pending intent that carries them: if Android kills the process while Termux
 * runs the command, the callback is gone. A host that needs to survive that
 * should use the broadcast adapter and persist the result itself, or set a
 * fallback listener.
 */
public final class TermuxResultDispatcher {

    private static final Map<Integer, TermuxResultCallback> CALLBACKS =
            new ConcurrentHashMap<>();

    private static volatile TermuxResultCallback fallback;

    private TermuxResultDispatcher() {
    }

    /**
     * Called with every result that has no per-command callback, for example
     * after process death or when the host deliberately uses broadcast
     * delivery. Set to null to clear.
     */
    public static void setFallbackCallback(TermuxResultCallback callback) {
        fallback = callback;
    }

    static void register(int requestCode, TermuxResultCallback callback) {
        if (callback != null) CALLBACKS.put(requestCode, callback);
    }

    static void unregister(int requestCode) {
        CALLBACKS.remove(requestCode);
    }

    /**
     * Reads Termux's result bundle, or returns null when the intent carries
     * none. The script name in {@code EXTRA_SCRIPT} is optional: use
     * {@code TermuxResult.parse} directly when it is absent.
     */
    public static TermuxResult parse(Intent intent) {
        if (intent == null) return null;
        Bundle bundle = intent.getBundleExtra(TermuxContract.RESULT_BUNDLE);
        if (bundle == null) return null;

        Integer exitCode = bundle.containsKey(TermuxContract.RESULT_EXIT_CODE)
                ? bundle.getInt(TermuxContract.RESULT_EXIT_CODE) : null;
        return TermuxResult.parse(
                intent.getStringExtra(TermuxResultService.EXTRA_SCRIPT),
                bundle.getString(TermuxContract.RESULT_STDOUT, ""),
                bundle.getString(TermuxContract.RESULT_STDERR, ""),
                exitCode,
                bundle.getInt(TermuxContract.RESULT_ERR, TermuxContract.ERR_OK),
                bundle.getString(TermuxContract.RESULT_ERRMSG, ""),
                SystemClock.elapsedRealtime());
    }

    static void dispatch(Context context, Intent intent) {
        TermuxResult result = parse(intent);
        if (result == null) return;

        int requestCode = intent.getIntExtra(TermuxResultService.EXTRA_REQUEST_CODE, -1);
        TermuxResultCallback callback =
                requestCode < 0 ? null : CALLBACKS.remove(requestCode);
        if (callback == null) callback = fallback;
        if (callback == null) return;

        try {
            callback.onTermuxResult(result);
        } catch (Throwable ignored) {
            // A result delivery must never take the process down.
        }
    }
}
