{ pkgs, apk, buildToolsVersion ? "35.0.0" }:
let
  sdk = (pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "35" ];
    buildToolsVersions = [ buildToolsVersion ];
    includeEmulator = false;
    includeSystemImages = false;
    includeSources = false;
    includeNDK = false;
    includeCmake = false;
  }).androidsdk;

  buildTools = "${sdk}/libexec/android-sdk/build-tools/${buildToolsVersion}";
in
pkgs.writeShellApplication {
  name = "install-termux-adb-bridge";

  runtimeInputs = [
    pkgs.android-tools
    pkgs.jdk
    pkgs.coreutils
  ];

  text = ''
    state="''${XDG_DATA_HOME:-$HOME/.local/share}/termux-adb-bridge"
    key="$state/keystore.jks"
    pkg=com.termux.adbbridge

    mkdir -p "$state"
    if [ ! -f "$key" ]; then
      echo "creating signing key at $key"
      keytool -genkeypair -keystore "$key" -alias bridge \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass bridgepass -keypass bridgepass \
        -dname "CN=Termux ADB Bridge, O=local" >/dev/null 2>&1
    fi

    work=$(mktemp -d)
    trap 'rm -rf "$work"' EXIT

    echo "signing ${apk}/termux-adb-bridge-unsigned.apk"
    java -jar ${buildTools}/lib/apksigner.jar sign \
      --ks "$key" --ks-pass pass:bridgepass --key-pass pass:bridgepass \
      --out "$work/bridge.apk" \
      ${apk}/termux-adb-bridge-unsigned.apk

    java -jar ${buildTools}/lib/apksigner.jar verify "$work/bridge.apk" >/dev/null
    echo "signature verified"

    if ! adb get-state >/dev/null 2>&1; then
      echo "error: no adb device. Plug the phone in and run: adb devices" >&2
      exit 1
    fi

    # A previously installed copy may be signed with a different key (for
    # example the one generated on the phone); replacing it needs a reinstall.
    if ! adb install -r "$work/bridge.apk" 2>&1 | tee /dev/stderr | grep -q '^Success'; then
      echo "replacing the existing installation (signature changed)"
      adb uninstall "$pkg" >/dev/null 2>&1 || true
      adb install "$work/bridge.apk"
    fi

    adb shell pm grant "$pkg" com.termux.permission.RUN_COMMAND
    echo
    echo "installed. check with:"
    echo "  adb shell am start -n $pkg/.Bridge --es cmd 'whoami'"
  '';
}
