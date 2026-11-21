# termux-adb-bridge

Run shell commands inside [Termux](https://termux.dev) on a stock, non-rooted Android
phone **from a PC over plain adb** — no SSH, no `run-as`, no root, no debuggable build.

```console
$ ./phone-cmd 'whoami; pwd'
u0_a514
/data/data/com.termux/files/home
```

## Why this is needed

Android's `adb shell` runs as the `shell` user. It cannot read Termux's private
`/data/data/com.termux/files/home` (permission denied), cannot `run-as com.termux`
(production Termux is not debuggable), and cannot use `su` on a locked-down device.
It also cannot hold `com.termux.permission.RUN_COMMAND`, so it cannot call Termux's
`RunCommandService` intent directly — `pm grant com.android.shell …` silently does nothing.

Termux's own external-command API needs a **caller app** that holds that permission.
This repo is that caller: a ~13 KB app that turns an adb-launched intent into a
`RUN_COMMAND` intent, executed as the Termux user in the Termux home directory.

```
adb shell am start -n com.termux.adbbridge/.Bridge --es cmd 'ls ~'
  └─ com.termux.adbbridge            (holds RUN_COMMAND, started by adb shell)
      └─ com.termux.RUN_COMMAND      (Termux RunCommandService)
          └─ sh -c 'ls ~'            (uid of com.termux, cwd ~)
```

## Requirements

- Termux from F-Droid or GitHub (any recent version), with `allow-external-apps=true`
  in `~/.termux/termux.properties`; run `termux-reload-settings` afterwards.
- The device must be authorized for adb (`adb devices` → `device`).
- Termux running (not force-stopped). Backgrounded is fine.

## Build

Build **on the phone**, inside Termux. That is deliberate: the x86_64 SDK build-tools
shipped in most Android SDK installs do not run on an arm64 phone, while Termux's
`aapt2`, `d8` and `apksigner` do, and an `android.jar` from the SDK platform directory
is enough — no Gradle, no Android Studio.

```sh
pkg install openjdk-21 aapt2 d8 apksigner   # if not already present
# android.jar is required:
#   sdkmanager "platforms;android-35"   (or copy one from a PC)
cd termux-adb-bridge
sh build.sh                                  # -> out/bridge.apk (signed, verified)
```

`build.sh` auto-detects the newest `platforms/*/android.jar` under `$HOME/android-sdk`
and prefers Termux-native tools over the SDK's.

The signing key is created once at `keystore.jks` **outside** `out/`, so rebuilds keep
the same signature and `adb install -r` keeps working. It is git-ignored on purpose:
regenerate your own rather than trusting this one.

## Install

A locally built, self-signed APK trips Google Play Protect ("built for an older version
of Android") when `targetSdkVersion` is low, so the manifest targets 35. If Play Protect
still blocks the install, temporarily disable adb install verification:

```sh
adb shell settings put global verifier_verify_adb_installs 0
adb install -r out/bridge.apk        # or pull it first: adb pull /data/data/... 
adb shell settings put global verifier_verify_adb_installs 1
```

Then grant the one permission the whole trick depends on:

```sh
adb shell pm grant com.termux.adbbridge com.termux.permission.RUN_COMMAND
```

## Use

`phone-cmd` (PC side, bash + adb) runs a command and prints its output and exit status:

```sh
./phone-cmd 'uname -m; whoami'
./phone-cmd 'for i in 1 2 3; do echo $i; done | tac'
./phone-cmd 'false'; echo "exit=$?"     # -> exit=1
```

Or call the bridge directly, fire-and-forget:

```sh
adb shell "am start -n com.termux.adbbridge/.Bridge --es cmd 'echo hi > /sdcard/hi.txt'"
```

Notes on quoting: adb re-parses its arguments with the device shell, so the command has
to be single-quoted for the phone. `phone-cmd` handles that, including embedded quotes.

## Two device quirks this works around

Both were found the hard way on a Blackview device running Android 15:

1. **Play Protect refuses APKs with a low `targetSdkVersion`.** The dialog says "This app
   was built for an older version of Android"; `adb install` hangs with no error until
   the dialog is dismissed. Fixed by targeting SDK 35.
2. **OEM auto-start firewalls block broadcast receivers.** The firmware logged
   `BVPFWLogger: AppAutoStartupPolicy: prevent start receiver of package …`, so the
   first version — a `BroadcastReceiver` triggered by `am broadcast` — was never invoked,
   while the broadcast reported success. The bridge is therefore a `Theme.NoDisplay`
   activity, which an explicit `am start` gets through.

## Security

The activity is guarded by `android.permission.DUMP`, which adb shell holds and ordinary
apps do not, so installing this app does not give random apps on the phone a command
channel into Termux. It does mean **anything with adb access can run commands as Termux** —
which is already true of `adb` itself, but worth stating. Uninstall with
`adb uninstall com.termux.adbbridge` when done.

## Files

| Path | Purpose |
|---|---|
| `AndroidManifest.xml` | Package, permission request, `DUMP`-guarded activity |
| `src/com/termux/adbbridge/Bridge.java` | Reads `cmd`, converts to a `RUN_COMMAND` intent |
| `build.sh` | aapt2 → javac → d8 → sign, on-device, no Gradle |
| `phone-cmd` | PC-side wrapper: runs a command, captures output and exit code |

## Limitations

- Termux must be running; a force-stopped Termux cannot be started this way.
- Output is relayed through a temp file in `/sdcard`, so a command that never exits
  keeps `phone-cmd` waiting until its timeout.
- `RUN_COMMAND_SESSION_ACTION` is `0`, i.e. the command's output appears in a session
  but the terminal is not stolen from whatever you were doing.
