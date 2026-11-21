{
  description = "Caller app that lets a PC run commands inside Termux on a non-rooted Android phone over adb";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      # The Android SDK is unfreeRedistributable and its licence must be
      # accepted for composeAndroidPackages to evaluate.
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
      apk = import ./nix/apk.nix { inherit pkgs; src = ./.; };
      installer = import ./nix/install.nix { inherit pkgs apk; };
    in
    {
      packages.${system} = {
        termux-adb-bridge = apk;
        install-termux-adb-bridge = installer;
        default = apk;
      };

      apps.${system}.default = {
        type = "app";
        program = "${installer}/bin/install-termux-adb-bridge";
      };

      formatter.${system} = pkgs.nixfmt;
    };
}
