package io.github.mbanucu.termux;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the one-shot, mutable {@link PendingIntent} Termux fills with a
 * result.
 *
 * <p>Two details are load-bearing:
 *
 * <ul>
 *   <li><b>One shot.</b> A consumed one-shot pending intent cannot deliver a
 *       second result; the caller asks for a new one per command.</li>
 *   <li><b>Unique request code.</b> {@link PendingIntent} equality ignores
 *       extras, so the same base intent and request code would return the
 *       already-consumed intent. Every command gets the next code from one
 *       process-wide counter.</li>
 *   <li><b>Mutable on Android 12+.</b> Termux has to attach the {@code result}
 *       bundle to the intent the pending intent wraps.</li>
 * </ul>
 */
public final class TermuxPendingIntents {

    private static final AtomicInteger NEXT_REQUEST_CODE = new AtomicInteger(0);

    private TermuxPendingIntents() {
    }

    /** The next request code; unique within this process. */
    public static int nextRequestCode() {
        return NEXT_REQUEST_CODE.getAndIncrement();
    }

    /** A one-shot pending intent that starts a service. */
    public static PendingIntent forService(Context context, Intent base, int requestCode) {
        return PendingIntent.getService(context, requestCode, base, flags());
    }

    /** A one-shot pending intent that sends a broadcast. */
    public static PendingIntent forBroadcast(Context context, Intent base, int requestCode) {
        return PendingIntent.getBroadcast(context, requestCode, base, flags());
    }

    private static int flags() {
        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return flags;
    }
}
