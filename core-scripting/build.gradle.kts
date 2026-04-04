plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

kotlin {
    androidTarget()
    jvm("desktop")
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) {
        browser()
        binaries.library()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core-model"))
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.graalvm.polyglot)
                implementation(libs.graalvm.js)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.graalvm.polyglot)
                implementation(libs.graalvm.js)
            }
        }
    }
}

android {
    namespace = "com.reqlab.core.scripting"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

tasks.matching { it.name == "jsBrowserTest" || it.name == "wasmJsBrowserTest" }
    .configureEach {
        enabled = false
    }

val runAppleSimulatorTests = providers.gradleProperty("runAppleSimulatorTests")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

tasks.matching { it.name == "iosSimulatorArm64Test" || it.name == "iosX64Test" }
    .configureEach {
        enabled = runAppleSimulatorTests.get()
    }
