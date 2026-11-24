plugins {
    alias(libs.plugins.android.library) apply false
}

// Both modules are published as io.github.mbanucu:<module>:<version>.
allprojects {
    group = "io.github.mbanucu"
    version = providers.gradleProperty("VERSION_NAME").getOrElse("0.1.0-SNAPSHOT")
}
