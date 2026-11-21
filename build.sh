#!/data/data/com.termux/files/usr/bin/sh
# Build/sign com.termux.adbbridge on the phone itself (no gradle).
set -e

SDK="$HOME/android-sdk"
ANDROID_JAR="$(ls "$SDK"/platforms/*/android.jar 2>/dev/null | sort -V | tail -1)"
# Prefer Termux's native arm64 build tools; the SDK's build-tools are x86_64 and won't run here.
AAPT2="$(command -v aapt2 || echo "$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)/aapt2")"
D8="$(command -v d8 || echo "$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)/d8")"
APKSIGNER="$(command -v apksigner || echo "$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)/apksigner")"
echo "aapt2:     $AAPT2"
echo "d8:        $D8"
echo "apksigner: $APKSIGNER"
echo "android.jar: $ANDROID_JAR"
[ -n "$ANDROID_JAR" ] || { echo "android.jar missing"; exit 1; }

cd "$(dirname "$0")"
rm -rf out
mkdir -p out/classes

echo "--- aapt2 link ---"
"$AAPT2" link -o out/base.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml

echo "--- javac ---"
javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR" \
      -d out/classes src/com/termux/adbbridge/Bridge.java 2>&1 | grep -v "^warning:" || true
ls out/classes/com/termux/adbbridge/

echo "--- d8 ---"
"$D8" --lib "$ANDROID_JAR" --min-api 21 --output out out/classes/com/termux/adbbridge/*.class

echo "--- package dex into apk ---"
( cd out && jar uf base.apk classes.dex && ls -l base.apk )

echo "--- sign ---"
# Keystore lives outside out/ so rebuilds keep the same signature.
KEYSTORE="$(pwd)/keystore.jks"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias bridge \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass bridgepass -keypass bridgepass \
    -dname "CN=Termux ADB Bridge, O=local" 2>&1 | tail -2
fi
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:bridgepass \
      --key-pass pass:bridgepass --out out/bridge.apk out/base.apk
"$APKSIGNER" verify out/bridge.apk && echo "SIGNED_OK"
ls -l "$(pwd)/out/bridge.apk"
