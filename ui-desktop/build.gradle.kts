plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop") {
        mainRun {
            mainClass = "com.reqlab.ui.desktop.MainKt"
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(project(":ui-shared"))
                implementation(libs.coroutines.core)
                implementation(libs.coroutines.swing)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit4)
                implementation("org.jetbrains.compose.ui:ui-test-junit4-desktop:1.8.1")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.reqlab.ui.desktop.MainKt"
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
