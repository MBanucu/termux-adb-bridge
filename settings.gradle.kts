// The on-phone `build.sh` and the Nix flake do not use Gradle. This build is
// only for the two reusable library modules and for publishing them.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "termux-run-command"

include(":termux-result")
include(":termux-run-command")
