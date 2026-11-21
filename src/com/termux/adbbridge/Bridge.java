package com.termux.adbbridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/**
 * Triggered with:
 *   adb shell am start -n com.termux.adbbridge/.Bridge \
 *       --es cmd 'echo hi > /sdcard/x'
 *
 * Forwards the command to Termux as a RUN_COMMAND intent, executed by
 * $PREFIX/bin/sh -c "<cmd>" in the Termux home directory, then finishes.
 *
 * This app holds com.termux.permission.RUN_COMMAND, which adb's shell UID
 * cannot hold -- that is what makes the path work.
 *
 * Implemented as an Activity rather than a BroadcastReceiver: the Blackview
 * firmware's BVPFW AppAutoStartupPolicy blocks background receiver starts
 * ("prevent start receiver of package ..."), while an explicitly started
 * activity goes through.
 */
public class Bridge extends Activity {

    private static final String TERMUX_PKG = "com.termux";
    private static final String TERMUX_SERVICE = "com.termux.app.RunCommandService";
    private static final String ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    private static final String TERMUX_SH = "/data/data/com.termux/files/usr/bin/sh";
    private static final String TERMUX_WORKDIR = "/data/data/com.termux/files/home";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deliver(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        deliver(intent);
        finish();
    }

    private void deliver(Intent intent) {
        if (intent == null) {
            return;
        }
        String cmd = intent.getStringExtra("cmd");
        if (cmd == null || cmd.trim().isEmpty()) {
            return;
        }

        Intent run = new Intent();
        run.setClassName(TERMUX_PKG, TERMUX_SERVICE);
        run.setAction(ACTION_RUN_COMMAND);
        run.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_SH);
        run.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", cmd});
        run.putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_WORKDIR);
        run.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        run.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        run.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "adb");

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(run);
        } else {
            startService(run);
        }
    }
}
