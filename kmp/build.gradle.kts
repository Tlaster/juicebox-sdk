import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    `maven-publish`
    signing
}

group = findProperty("groupId")?.toString() ?: "moe.tlaster"
version = findProperty("versionString")?.toString() ?: "0.3.6-SNAPSHOT"

val ktorVersion = "3.0.0"
val wasmPackageVersion = findProperty("juiceboxWasmVersion")?.toString() ?: version.toString().removeSuffix("-SNAPSHOT")
val isSnapshotVersion = version.toString().endsWith("-SNAPSHOT")

fun propertyOrEnvironment(
    propertyName: String,
    environmentName: String,
): String? =
    findProperty(propertyName)
        ?.toString()
        ?.takeIf { it.isNotBlank() }
        ?: System.getenv(environmentName)?.takeIf { it.isNotBlank() }

fun ossrhRepositoryUrl(): String =
    findProperty("ossrhRepo")
        ?.toString()
        ?.takeIf { it.isNotBlank() }
        ?: if (isSnapshotVersion) {
            findProperty("ossrhSnapshotRepo")
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: "https://central.sonatype.com/repository/maven-snapshots/"
        } else {
            findProperty("ossrhReleaseRepo")
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
        }

val nativeCargoTargets =
    mapOf(
        "iosArm64" to "aarch64-apple-ios",
        "iosX64" to "x86_64-apple-ios",
        "iosSimulatorArm64" to "aarch64-apple-ios-sim",
        "macosArm64" to "aarch64-apple-darwin",
        "macosX64" to "x86_64-apple-darwin",
        "linuxArm64" to "aarch64-unknown-linux-gnu",
        "linuxX64" to "x86_64-unknown-linux-gnu",
    )

val jvmJniResourcesDir = layout.projectDirectory.dir("../artifacts/jni-host")

kotlin {
    jvmToolchain(17)

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }
    linuxX64()
    linuxArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        val cargoTarget = nativeCargoTargets.getValue(name)
        val ffiArtifactDir = layout.projectDirectory.dir("../artifacts/ffi/$cargoTarget").asFile

        binaries.all {
            linkerOpts("-L${ffiArtifactDir.absolutePath}", "-ljuicebox_sdk_ffi")
        }

        compilations.getByName("main") {
            cinterops {
                create("juicebox") {
                    packageName("xyz.juicebox.sdk.ffi")
                    defFile(project.file("src/nativeInterop/cinterop/juicebox.def"))
                    includeDirs(project.file("../swift/Sources/JuiceboxSdkFfi"))
                }
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        androidMain {
            kotlin.srcDir("../android/src/main/kotlin")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        jvmMain {
            kotlin.srcDir("../android/src/main/kotlin")
            kotlin.exclude("xyz/juicebox/sdk/Client.kt")
            resources.srcDir(jvmJniResourcesDir)
            resources.exclude("*.dylib", "*.so", "*.dll")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        nativeMain {
            dependencies {
                implementation("io.ktor:ktor-client-core:$ktorVersion")
            }
        }
        appleMain {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:$ktorVersion")
            }
        }
        linuxMain {
            dependencies {
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
        wasmJsMain {
            dependencies {
                implementation(npm("juicebox-sdk", wasmPackageVersion))
            }
        }
    }
}

android {
    namespace = "xyz.juicebox.sdk.kmp"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    sourceSets {
        getByName("main") {
            java.srcDir("../android/src/main/java")
            jniLibs.srcDir("../artifacts/jni")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "OSSRH"
            url = uri(ossrhRepositoryUrl())
            credentials {
                username = propertyOrEnvironment("ossrhUsername", "OSSRH_USERNAME").orEmpty()
                password = propertyOrEnvironment("ossrhPassword", "OSSRH_PASSWORD").orEmpty()
            }
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("sdk-kmp")
            description.set("Kotlin Multiplatform facade for the Juicebox SDK")
            url.set("https://github.com/Tlaster/juicebox-sdk")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://github.com/Tlaster/juicebox-sdk/blob/main/LICENSE")
                }
            }
            developers {
                developer {
                    name.set("Tlaster")
                }
            }
            scm {
                url.set("https://github.com/Tlaster/juicebox-sdk")
                connection.set("scm:git:https://github.com/Tlaster/juicebox-sdk.git")
                developerConnection.set("scm:git:git@github.com:Tlaster/juicebox-sdk.git")
            }
        }
    }
}

signing {
    val signingKey = propertyOrEnvironment("signingKey", "SIGNING_KEY")
    val signingPassword = propertyOrEnvironment("signingPassword", "SIGNING_PASSWORD")

    isRequired = !isSnapshotVersion

    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
