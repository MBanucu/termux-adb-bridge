plugins {
    `java-library`
    `maven-publish`
    signing
}

java {
    // Java 8 keeps the artifact usable by every Android host, including the
    // on-phone build.sh path, which compiles with -source 8.
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

val isRelease = !version.toString().endsWith("-SNAPSHOT")

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            artifactId = "termux-result"

            pom {
                name.set("Termux Result")
                description.set(
                    "Device-free parser for the result bundle a Termux RUN_COMMAND returns"
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
                    connection.set("scm:git:https://github.com/MBanucu/termux-adb-bridge.git")
                    developerConnection.set(
                        "scm:git:ssh://git@github.com/MBanucu/termux-adb-bridge.git"
                    )
                    url.set("https://github.com/MBanucu/termux-adb-bridge")
                }
            }
        }
    }

    // The Central Portal upload is a release follow-up. Nothing is signed or
    // uploaded here unless the release job provides the signing key.
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

    if (isRelease && providers.gradleProperty("signing.keyId").isPresent) {
        signing {
            sign(publishing.publications["release"])
        }
    }
}
