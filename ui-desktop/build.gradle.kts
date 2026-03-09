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

// ─── Desktop application + native packaging ───────────────────────────────────
// Version flows from gradle.properties → root build.gradle.kts → project.version
// Change it in ONE place: gradle.properties  appVersion=x.y.z
compose.desktop {
    application {
        mainClass = "com.reqlab.ui.desktop.MainKt"

        nativeDistributions {
            // Target every platform; each CI runner builds only what it supports.
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,  // macOS
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,  // Windows
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,  // Linux (Debian)
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,  // Linux (Fedora)
            )

            packageName    = "ReqLab"
            packageVersion = project.version.toString()   // e.g. "1.0.0"
            description    = "ReqLab – API testing for every platform"
            copyright      = "ReqLab Contributors"
            vendor         = "ReqLab"

            macOS {
                bundleID = "com.reqlab.reqlab"
                // dmgPackageVersion inherits packageVersion automatically
            }
            windows {
                dirChooser         = true
                perUserInstall     = true
                shortcut           = true
                menuGroup          = "ReqLab"
            }
            linux {
                packageName        = "reqlab"
                debMaintainer      = "reqlab@users.noreply.github.com"
                appCategory        = "Development"
            }
        }
    }
}

// ─── JAR rename task ──────────────────────────────────────────────────────────
// Produces  ui-desktop/build/distribute/ReqLab-{version}.jar
// Output goes to a separate directory to avoid interfering with createDistributable.
tasks.register<Copy>("packageReqLabJar") {
    dependsOn("desktopJar")
    val ver = project.version.toString()
    from(layout.buildDirectory.dir("libs")) {
        // The Kotlin/Gradle jar is named  ui-desktop-desktop-{version}.jar
        include("ui-desktop-desktop-${ver}.jar")
        rename { "ReqLab-${ver}.jar" }
    }
    into(layout.buildDirectory.dir("distribute"))
    description = "Copies and renames the desktop JAR to ReqLab-{version}.jar in build/distribute"
    group       = "distribution"
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
