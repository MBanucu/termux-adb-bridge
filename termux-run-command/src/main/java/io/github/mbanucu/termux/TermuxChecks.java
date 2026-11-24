package io.github.mbanucu.termux;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * The Android-side checks that surround a RUN_COMMAND, kept next to the intent
 * builder so hosts do not each re-invent them.
 *
 * <p>The pure parts - the permission name, version parsing and the
 * {@code allow-external-apps} parser - live in {@link TermuxContract}. The
 * property itself cannot be read by another app (it is inside Termux's private
 * home), so only the parser is offered here.
 */
public final class TermuxChecks {

    private TermuxChecks() {
    }

    /** True when Termux is installed, whether or not it can be reached. */
    public static boolean isTermuxInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(TermuxContract.PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * True when this app holds {@code com.termux.permission.RUN_COMMAND}.
     * The permission is declared by Termux, so it does not show in the normal
     * runtime-permission dialog; grant it with adb or from Termux's settings.
     */
    public static boolean hasRunCommandPermission(Context context) {
        return context.getPackageManager().checkPermission(
                TermuxContract.PERMISSION_RUN_COMMAND, context.getPackageName())
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Termux's version name, or {@code "unknown"} when it cannot be read. */
    public static String termuxVersion(Context context) {
        try {
            String version = context.getPackageManager()
                    .getPackageInfo(TermuxContract.PACKAGE, 0).versionName;
            return version == null ? "unknown" : version;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    /**
     * True when Termux is new enough to return a result bundle (0.109+).
     * An unreadable version is assumed supported; see
     * {@link TermuxContract#supportsResult(String)}.
     */
    public static boolean supportsResult(Context context) {
        return TermuxContract.supportsResult(termuxVersion(context));
    }

    /** The adb command that grants the permission to this app. */
    public static String grantCommand(Context context) {
        return "pm grant " + context.getPackageName() + " "
                + TermuxContract.PERMISSION_RUN_COMMAND;
    }

    /** A single human-readable description of what is missing, or null. */
    public static String problem(Context context) {
        if (!isTermuxInstalled(context)) {
            return "Termux is not installed; install it from F-Droid or GitHub";
        }
        if (!hasRunCommandPermission(context)) {
            return "missing " + TermuxContract.PERMISSION_RUN_COMMAND
                    + "; run: adb shell " + grantCommand(context);
        }
        // allow-external-apps lives in Termux's private home, so no app can
        // check it. Show TermuxContract.ALLOW_EXTERNAL_APPS_COMMAND instead.
        return null;
    }
}
