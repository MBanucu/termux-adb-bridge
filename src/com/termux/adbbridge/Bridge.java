package com.termux.adbbridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import io.github.mbanucu.termux.TermuxCommand;
import io.github.mbanucu.termux.TermuxContract;

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
 *
 * The intent itself now comes from the termux-run-command library, which is
 * compiled into this APK by build.sh and by nix/apk.nix.
 */
public class Bridge extends Activity {

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

        TermuxCommand.builder(this)
                .script(TermuxContract.DEFAULT_SH)
                .args("-c", cmd)
                .workdir(TermuxContract.DEFAULT_HOME)
                .background(true)
                .sessionAction(0)
                .label("adb")
                .foregroundService(true)
                .send();
    }
}
