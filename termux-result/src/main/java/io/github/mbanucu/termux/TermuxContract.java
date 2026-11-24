package io.github.mbanucu.termux;

/**
 * The parts of Termux's external-command contract that are plain strings and
 * plain checks: package, service, action, permission, result bundle keys,
 * version parsing and the {@code allow-external-apps} setting.
 *
 * <p>Everything here is deliberately free of Android imports so it can be
 * tested on a host JVM. The Android-side checks ({@code PackageManager},
 * permission state, actually starting the service) live in
 * {@code io.github.mbanucu.termux.TermuxChecks} in the
 * {@code termux-run-command} module.
 */
public final class TermuxContract {
    /** Termux's application id. */
    public static final String PACKAGE = "com.termux";

    /** Termux's {@code RunCommandService}, addressed explicitly. */
    public static final String RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";

    /** Action accepted by {@code RunCommandService}. */
    public static final String ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";

    /**
     * Permission the calling app must hold. It is declared by Termux and can
     * only be granted by the user or by adb:
     * {@code adb shell pm grant <caller> com.termux.permission.RUN_COMMAND}.
     */
    public static final String PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND";

    /** Extra bundle Termux uses for the result of a background command. */
    public static final String RESULT_BUNDLE = "result";
    public static final String RESULT_STDOUT = "stdout";
    public static final String RESULT_STDERR = "stderr";
    public static final String RESULT_EXIT_CODE = "exitCode";
    public static final String RESULT_ERR = "err";
    public static final String RESULT_ERRMSG = "errmsg";

    /** Termux's "no internal error" value, {@code Activity.RESULT_OK}. */
    public static final int ERR_OK = -1;

    /** First Termux minor version that sends the result bundle (0.109). */
    public static final int RESULT_MINOR_VERSION = 109;

    /** Termux's private prefix and home, as seen from inside Termux. */
    public static final String DEFAULT_PREFIX = "/data/data/com.termux/files/usr";
    public static final String DEFAULT_HOME = "/data/data/com.termux/files/home";
    public static final String DEFAULT_SH = DEFAULT_PREFIX + "/bin/sh";
    public static final String DEFAULT_BASH = DEFAULT_PREFIX + "/bin/bash";

    /** Property key in {@code ~/.termux/termux.properties}. */
    public static final String ALLOW_EXTERNAL_APPS_KEY = "allow-external-apps";

    /**
     * The exact line a user can run inside Termux to opt in. Kept here so
     * every host shows the same instruction.
     */
    public static final String ALLOW_EXTERNAL_APPS_COMMAND =
            "echo 'allow-external-apps=true' >> ~/.termux/termux.properties"
                    + " && termux-reload-settings";

    private TermuxContract() {
    }

    /**
     * The minor version of a Termux version name: {@code "0.118.1"} and
     * {@code "0.109"} both give 118 and 109. Returns {@code -1} when the
     * version cannot be understood.
     */
    public static int minorOf(String version) {
        if (version == null) return -1;
        String[] parts = version.split("\\.");
        if (parts.length < 2) return -1;
        String digits = parts[1].replaceAll("[^0-9].*$", "");
        if (digits.isEmpty()) return -1;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * True when Termux is new enough to send a result through the
     * {@code PendingIntent}. An unparseable version is treated as supported:
     * Termux ignores an unknown extra on old versions, so assuming support
     * costs nothing while refusing would disable a working host.
     */
    public static boolean supportsResult(String version) {
        int minor = minorOf(version);
        return minor < 0 || minor >= RESULT_MINOR_VERSION;
    }

    /**
     * Parses {@code termux.properties} text and returns whether
     * {@code allow-external-apps=true} is set. This is a pure check: an app
     * cannot read Termux's private properties file, so callers use it on a
     * copy, a user-provided paste, or their own setup text.
     *
     * <p>Comments ({@code #} or {@code !}) and blank lines are ignored; the
     * last assignment wins, as it does in a Java properties file.
     */
    public static boolean allowExternalApps(CharSequence properties) {
        if (properties == null) return false;
        boolean allowed = false;
        for (String raw : properties.toString().replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            if (!ALLOW_EXTERNAL_APPS_KEY.equals(key)) continue;
            allowed = "true".equalsIgnoreCase(line.substring(eq + 1).trim());
        }
        return allowed;
    }
}
