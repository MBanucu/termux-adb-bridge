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

## Library modules

The `RUN_COMMAND` intent, its result bundle and the permission/version checks
are also published as two reusable modules, so the other apps that need the
same integration do not copy the contract again:

| Module | What it contains |
|---|---|
| `termux-result` | Pure Java `TermuxResult.parse(...)`, the `RESULT key=value` parser, and the pure permission/version/`allow-external-apps` checks. No Android dependency, so it is tested on a host JVM. |
| `termux-run-command` | Android library: `TermuxCommand.builder(context)`, the one-shot mutable `PendingIntent` helper with unique request codes, `TermuxResultService`/`TermuxResultReceiver` adapters, and `TermuxChecks`. Intent keys are declared only in `TermuxIntentSpec`. |

### Consume it

```kotlin
dependencies {
    implementation("io.github.mbanucu:termux-result:0.1.0-SNAPSHOT")
    implementation("io.github.mbanucu:termux-run-command:0.1.0-SNAPSHOT")
}
```

```java
TermuxCommand.builder(context)
    .script(TermuxContract.DEFAULT_BASH)
    .args("-lc", "echo hello")
    .workdir(TermuxContract.DEFAULT_HOME)
    .background(true)
    .label("hello")
    .send(result -> Log.i("app", result.uiLine()));
```

`send()` is fire-and-forget. `send(callback)` uses the service adapter and a
one-shot pending intent; `sendTo(Receiver.class)` uses the broadcast adapter
instead (the receiver must be declared in the host manifest). The callback is
process-local: if Android kills the host before Termux answers, the result is
lost. Use `TermuxResultDispatcher.setFallbackCallback(...)` or the broadcast
adapter with persisted state when that matters.

No version has been uploaded to Maven Central yet; see the follow-ups below.

### The contract, in one place

- **Termux version:** Termux 0.109 or newer is required for a result bundle.
  Older versions still run the command but never answer; a host can treat the
  side effect (or the process output) as the fallback.
  `TermuxContract.supportsResult(version)` implements this check, and an
  unreadable version is assumed modern because older Termux ignores the extra.
- **Permission:** the caller must hold `com.termux.permission.RUN_COMMAND`,
  declared by Termux. Grant it with
  `adb shell pm grant <package> com.termux.permission.RUN_COMMAND` or from
  Termux's own settings. This is not a normal runtime permission and will not
  appear in the app's permission dialog.
- **Termux setting:** `allow-external-apps=true` must be in
  `~/.termux/termux.properties`, followed by `termux-reload-settings`. That file
  is in Termux's private home, so an app cannot read it; `TermuxContract`
  offers the pure parser and the exact opt-in command, while
  `TermuxChecks` cannot check it for you.
- **Intent shape:** path, arguments, workdir, background flag, runner, label,
  description, session action and result pending intent are the documented
  Termux extras. They live only in `TermuxIntentSpec`; the CI guard
  `scripts/check-run-command-constants.sh` fails if the prefix is re-typed
  elsewhere in this repository.

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
and prefers Termux-native tools over the SDK's. It compiles `Bridge.java` together
with both library modules using plain `javac`; the APK path does not need Gradle.

The signing key is created once at `keystore.jks` **outside** `out/`, so rebuilds keep
the same signature and `adb install -r` keeps working. It is git-ignored on purpose:
regenerate your own rather than trusting this one.

### Build the library modules

The APK itself still needs no Gradle, but the reusable modules have a Gradle build
next to it:

```sh
./gradlew :termux-result:build :termux-run-command:assembleRelease
./gradlew :termux-result:test :termux-run-command:testReleaseUnitTest
```

On Termux, create an untracked `local.properties` pointing at the SDK and at the
native `aapt2` (the one AGP downloads is x86_64):

```properties
sdk.dir=/data/data/com.termux/files/home/android-sdk
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

Both modules have `maven-publish` configured with POM metadata, sources and javadoc
artifacts. Generating those files is local and does not upload anything:

```sh
./gradlew :termux-result:generatePomFileForReleasePublication \
          :termux-run-command:generatePomFileForReleasePublication
```

`CI` runs the device-free tests, the contract guard and the publication-metadata
tasks. Nothing is signed or uploaded unless a release job provides the signing and
Central credentials.

### Build with Nix

An x86_64 Nix host can build the same APK reproducibly, no phone or Android Studio needed:

```console
$ nix build github:MBanucu/termux-adb-bridge        # -> termux-adb-bridge-unsigned.apk
```

The flake pulls the Android SDK from nixpkgs (`composeAndroidPackages`, build-tools
35.0.0, platform 35), so it needs `allowUnfree` and `android_sdk.accept_license`, which
the flake sets for its own package set. `d8` and `apksigner` are invoked as jars, because
Google ships them as `/bin/sh` wrappers that do not exist in the Nix sandbox.

The output is **unsigned** on purpose: signing is a separate step so the build stays
reproducible and no private key lands in the Nix store.

## Install

From Nix, signing and installing are one step:

```console
$ nix run github:MBanucu/termux-adb-bridge
```

That creates a key at `~/.local/share/termux-adb-bridge/keystore.jks` if needed, signs
the APK, installs it (uninstalling a differently-signed copy first, which is what happens
if you previously built it on the phone), and grants the Termux permission. Everything
below is the manual equivalent.

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
| `src/com/termux/adbbridge/Bridge.java` | Reads `cmd`, sends it through `TermuxCommand` |
| `build.sh` | aapt2 → javac → d8 → sign, on-device, no Gradle |
| `phone-cmd` | PC-side wrapper: runs a command, captures output and exit code |
| `termux-result/` | Pure Java result/contract module and its JVM tests |
| `termux-run-command/` | Android intent, pending-intent and result-adapter module |
| `scripts/check-run-command-constants.sh` | Fails when an extra key is written outside the library |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle/` | Multi-module Gradle build for the library modules only |

## Follow-ups

The extraction is in place here, but the roadmap issue stays open until the rest
of the acceptance criteria can be met:

- Publish `io.github.mbanucu:termux-result` and
  `io.github.mbanucu:termux-run-command` to Maven Central (portal credentials,
  signing key and a non-SNAPSHOT release).
- Migrate wireless-adb-keeper's `PinnedPort`/`TermuxResultService` and remove
  its local `TermuxResult` and `tools/ResultProbe.java`.
- Migrate opencode-termux-setup's `TermuxCommands`/`RunCommandReceiver`.
- Migrate android-accessible-terminal's sample `TermuxSession` intent variant.
- Turn the guard on in those repositories too, so the contract has one owner
  across all four consumers.

## Limitations

- Termux must be running; a force-stopped Termux cannot be started this way.
- Output is relayed through a temp file in `/sdcard`, so a command that never exits
  keeps `phone-cmd` waiting until its timeout.
- `RUN_COMMAND_SESSION_ACTION` is `0`, i.e. the command's output appears in a session
  but the terminal is not stolen from whatever you were doing.
