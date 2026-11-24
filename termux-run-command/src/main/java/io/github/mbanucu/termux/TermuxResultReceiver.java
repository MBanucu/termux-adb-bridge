package io.github.mbanucu.termux;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Broadcast adapter for a Termux result. Subclass it in the host app and use
 * {@link TermuxCommand#sendTo(Class)} so the one-shot pending intent targets
 * that subclass.
 *
 * <p>This is the shape for an app-uid caller that wants delivery even when its
 * callback object no longer exists; the host can persist or re-broadcast the
 * result itself. The receiver must be declared in the host manifest (usually
 * {@code android:exported="false"}).
 */
public abstract class TermuxResultReceiver extends BroadcastReceiver {

    @Override
    public final void onReceive(Context context, Intent intent) {
        TermuxResult result = TermuxResultDispatcher.parse(intent);
        if (result != null) {
            onTermuxResult(context, result);
        }
    }

    /** Called on the main thread with one parsed result. */
    protected abstract void onTermuxResult(Context context, TermuxResult result);
}
