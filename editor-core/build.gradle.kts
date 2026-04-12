plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm("desktop")

    js(IR) {
        browser()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
