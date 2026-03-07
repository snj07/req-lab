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
                implementation(compose.materialIconsExtended)
                implementation(project(":core-model"))
                implementation(project(":core-network"))
                implementation(project(":feature-requests"))
                implementation(project(":feature-collections"))
                implementation(project(":feature-history"))
                implementation(project(":feature-environments"))
                implementation(libs.coroutines.core)
                implementation(libs.coroutines.swing)
                implementation(libs.serialization.json)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
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
