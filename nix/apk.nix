# Builds com.termux.adbbridge from source with the Android SDK from nixpkgs.
# The result is unsigned: signing is deliberately kept out of the build so the
# output stays reproducible and no private key ends up in the Nix store or logs.
{
  pkgs,
  src,
  buildToolsVersion ? "35.0.0",
  platformVersion ? "35",
  minSdk ? 24,
}:
let
  # The SDK is unfree; the flake passes config.allowUnfree and
  # config.android_sdk.accept_license to this pkgs instance.
  sdk = (pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ platformVersion ];
    buildToolsVersions = [ buildToolsVersion ];
    includeEmulator = false;
    includeSystemImages = false;
    includeSources = false;
    includeNDK = false;
    includeCmake = false;
  }).androidsdk;

  # composeAndroidPackages lays the SDK out under libexec/android-sdk.
  buildTools = "${sdk}/libexec/android-sdk/build-tools/${buildToolsVersion}";
  androidJar = "${sdk}/libexec/android-sdk/platforms/android-${platformVersion}/android.jar";
  java = "${pkgs.jdk}/bin/java";
in
pkgs.stdenv.mkDerivation {
  pname = "termux-adb-bridge";
  version = "1.0";

  inherit src;

  nativeBuildInputs = [
    pkgs.jdk
    pkgs.zip
  ];

  # d8 and apksigner are shipped as shell wrappers with a /bin/sh shebang, which
  # does not exist in the build sandbox, so call their jars through java instead.
  buildPhase = ''
    runHook preBuild

    export TZ=UTC
    mkdir -p out/classes

    echo "--- aapt2 link ---"
    ${buildTools}/aapt2 link -o out/base.apk -I ${androidJar} \
      --manifest AndroidManifest.xml

    echo "--- javac ---"
    find src termux-result/src/main/java termux-run-command/src/main/java \
      -name '*.java' -print > sources.txt
    ${pkgs.jdk}/bin/javac -source 8 -target 8 \
      -bootclasspath ${androidJar} \
      -d out/classes @sources.txt

    echo "--- d8 ---"
    ${java} -cp ${buildTools}/lib/d8.jar com.android.tools.r8.D8 \
      --lib ${androidJar} --min-api ${toString minSdk} \
      --output out \
      $(find out/classes -name '*.class')

    echo "--- package dex ---"
    ( cd out && zip -X -q base.apk classes.dex )

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall
    install -Dm644 out/base.apk "$out/termux-adb-bridge-unsigned.apk"
    runHook postInstall
  '';

  meta = {
    description = "Caller app that lets adb run commands inside Termux (unsigned APK)";
    homepage = "https://github.com/MBanucu/termux-adb-bridge";
    license = pkgs.lib.licenses.mit;
    platforms = [ "x86_64-linux" ];
  };
}
