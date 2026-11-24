package io.github.mbanucu.termux;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Service adapter for a Termux result. {@link TermuxCommand} points its
 * one-shot {@code PendingIntent} here by default.
 *
 * <p>Declared non-exported by the library manifest: only the pending intent
 * created by this app (and filled in by Termux) can start it. A service is the
 * shape Termux's own RUN_COMMAND example uses, and it works while the app has
 * no visible activity - the reason wireless-adb-keeper uses it for pre-unlock
 * pinning.
 */
public class TermuxResultService extends Service {

    /** Full script path, copied from the command so the result can name it. */
    public static final String EXTRA_SCRIPT = "io.github.mbanucu.termux.SCRIPT";

    /** The request code whose callback should receive this result. */
    public static final String EXTRA_REQUEST_CODE = "io.github.mbanucu.termux.REQUEST_CODE";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            TermuxResultDispatcher.dispatch(this, intent);
        } finally {
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
