plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
    signing
}

android {
    namespace = "io.github.mbanucu.termux"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    // TermuxResult appears in the public API (callbacks and receivers), so it
    // must be an `api` dependency, not `implementation`.
    api(project(":termux-result"))
    testImplementation(libs.junit)
}

val isRelease = !version.toString().endsWith("-SNAPSHOT")

afterEvaluate {
    val androidJavadoc = tasks.register<Javadoc>("androidJavadoc") {
        group = "documentation"
        description = "Generates Javadoc for the Android library."
        android.sourceSets.getByName("main").java.srcDirs.forEach { source(it) }
        classpath = files(android.bootClasspath) +
            configurations.getByName("releaseCompileClasspath")
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:none", "-quiet")
            encoding = "UTF-8"
            charSet = "UTF-8"
        }
        destinationDir = layout.buildDirectory.dir("docs/javadoc").get().asFile
    }

    val javadocJar = tasks.register<Jar>("javadocJar") {
        group = "documentation"
        description = "Packages the Javadoc output for Maven Central."
        dependsOn(androidJavadoc)
        archiveClassifier.set("javadoc")
        from(androidJavadoc.map { it.destinationDir!! })
    }

    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "termux-run-command"
                artifact(javadocJar)

                pom {
                    name.set("Termux Run Command")
                    description.set(
                        "Intent builder, one-shot PendingIntent helper and result adapters " +
                            "for Termux's RUN_COMMAND service"
                    )
                    url.set("https://github.com/MBanucu/termux-adb-bridge")
                    licenses {
                        license {
                            name.set("MIT")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("MBanucu")
                            name.set("Mihai Banucu")
                            url.set("https://github.com/MBanucu")
                        }
                    }
                    scm {
                        connection.set(
                            "scm:git:https://github.com/MBanucu/termux-adb-bridge.git"
                        )
                        developerConnection.set(
                            "scm:git:ssh://git@github.com/MBanucu/termux-adb-bridge.git"
                        )
                        url.set("https://github.com/MBanucu/termux-adb-bridge")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "central"
                url = uri(
                    "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
                )
                credentials {
                    username = providers.gradleProperty("centralUsername").orNull
                    password = providers.gradleProperty("centralPassword").orNull
                }
            }
        }
    }

    if (isRelease && providers.gradleProperty("signing.keyId").isPresent) {
        signing {
            sign(publishing.publications["release"])
        }
    }
}
