package io.github.mbanucu.termux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Android-free description of one Termux {@code RUN_COMMAND}: action,
 * explicit component and extras.
 *
 * <p>{@link TermuxCommand} turns this into an {@link android.content.Intent};
 * keeping the shape here means the keys and their types can be tested on a
 * host JVM. Every extra is declared exactly once, in this class.
 */
public final class TermuxIntentSpec {

    /** Absolute path of the executable Termux should run. */
    public static final String EXTRA_PATH = "com.termux.RUN_COMMAND_PATH";
    /** String[] of arguments for that executable. */
    public static final String EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    /** Working directory, normally Termux's home. */
    public static final String EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    /** Boolean: run in the background runner rather than a terminal session. */
    public static final String EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    /** Optional runner name, for example {@code app-shell}. */
    public static final String EXTRA_RUNNER = "com.termux.RUN_COMMAND_RUNNER";
    /** Optional label shown in Termux's notification. */
    public static final String EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL";
    /** Optional longer description shown in Termux's notification. */
    public static final String EXTRA_COMMAND_DESCRIPTION =
            "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION";
    /** String: what Termux does with the session; "0" leaves the user alone. */
    public static final String EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION";
    /** PendingIntent Termux fills with the result bundle. */
    public static final String EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT";

    private final String action;
    private final String componentPackage;
    private final String componentClass;
    private final String script;
    private final List<String> arguments;
    private final String workdir;
    private final boolean background;
    private final String runner;
    private final String label;
    private final String description;
    private final Integer sessionAction;

    private TermuxIntentSpec(Builder builder) {
        this.action = TermuxContract.ACTION_RUN_COMMAND;
        this.componentPackage = TermuxContract.PACKAGE;
        this.componentClass = TermuxContract.RUN_COMMAND_SERVICE;
        this.script = builder.script;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(builder.arguments));
        this.workdir = builder.workdir;
        this.background = builder.background;
        this.runner = builder.runner;
        this.label = builder.label;
        this.description = builder.description;
        this.sessionAction = builder.sessionAction;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String action() {
        return action;
    }

    public String componentPackage() {
        return componentPackage;
    }

    public String componentClass() {
        return componentClass;
    }

    public String script() {
        return script;
    }

    public String workdir() {
        return workdir;
    }

    public boolean background() {
        return background;
    }

    /**
     * The extras exactly as they should be put on the Intent, in a stable
     * order. Values are {@link String}, {@link String}[] or {@link Boolean}.
     * Optional extras that were not set are absent.
     */
    public Map<String, Object> extras() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put(EXTRA_PATH, script);
        extras.put(EXTRA_ARGUMENTS, arguments.toArray(new String[0]));
        extras.put(EXTRA_WORKDIR, workdir);
        extras.put(EXTRA_BACKGROUND, background);
        if (runner != null) extras.put(EXTRA_RUNNER, runner);
        if (label != null) extras.put(EXTRA_COMMAND_LABEL, label);
        if (description != null) extras.put(EXTRA_COMMAND_DESCRIPTION, description);
        if (sessionAction != null) extras.put(EXTRA_SESSION_ACTION, sessionAction.toString());
        return Collections.unmodifiableMap(extras);
    }

    /** Mutable builder; {@link TermuxCommand.Builder} delegates to it. */
    public static final class Builder {
        private String script = TermuxContract.DEFAULT_SH;
        private final List<String> arguments = new ArrayList<>();
        private String workdir = TermuxContract.DEFAULT_HOME;
        private boolean background = true;
        private String runner;
        private String label;
        private String description;
        private Integer sessionAction;

        private Builder() {
        }

        /** Executable Termux runs; defaults to {@code $PREFIX/bin/sh}. */
        public Builder script(String path) {
            this.script = path;
            return this;
        }

        /** Replaces the argument list. */
        public Builder args(String... args) {
            arguments.clear();
            if (args != null) {
                Collections.addAll(arguments, args);
            }
            return this;
        }

        /** Replaces the argument list. */
        public Builder args(List<String> args) {
            arguments.clear();
            if (args != null) arguments.addAll(args);
            return this;
        }

        /** Working directory; defaults to Termux's home. */
        public Builder workdir(String path) {
            this.workdir = path;
            return this;
        }

        /** Defaults to true: a background command does not steal the screen. */
        public Builder background(boolean background) {
            this.background = background;
            return this;
        }

        /** Optional runner name, for example {@code app-shell}. */
        public Builder runner(String runner) {
            this.runner = runner;
            return this;
        }

        /** Optional label for Termux's notification. */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /** Optional description for Termux's notification. */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** "0" leaves the current terminal session alone. */
        public Builder sessionAction(int action) {
            this.sessionAction = action;
            return this;
        }

        public TermuxIntentSpec build() {
            if (script == null || script.trim().isEmpty()) {
                throw new IllegalArgumentException("script must not be empty");
            }
            if (workdir == null || workdir.trim().isEmpty()) {
                throw new IllegalArgumentException("workdir must not be empty");
            }
            for (String argument : arguments) {
                if (argument == null) {
                    throw new IllegalArgumentException("arguments must not contain null");
                }
            }
            return new TermuxIntentSpec(this);
        }
    }
}
