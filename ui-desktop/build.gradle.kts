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
                implementation(project(":sample-server"))
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
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
                iconFile.set(project.file("src/desktopMain/resources/icons/reqlab.icns"))
                // dmgPackageVersion inherits packageVersion automatically
            }
            windows {
                dirChooser         = true
                perUserInstall     = true
                shortcut           = true
                menuGroup          = "ReqLab"
                iconFile.set(project.file("src/desktopMain/resources/icons/reqlab.ico"))
            }
            linux {
                packageName        = "reqlab"
                debMaintainer      = "reqlab@users.noreply.github.com"
                appCategory        = "Development"
                iconFile.set(project.file("src/desktopMain/resources/icons/reqlab-icon-256.png"))
            }
        }
    }
}

// ─── Runnable fat JAR task ───────────────────────────────────────────────────
// Produces ui-desktop/build/distribute/ReqLab-{version}.jar
// Includes app classes + runtime classpath and sets Main-Class so `java -jar` works.
tasks.register<org.gradle.jvm.tasks.Jar>("packageReqLabJar") {
    val mainClassName = "com.reqlab.ui.desktop.MainKt"
    val desktopJarTask = tasks.named<org.gradle.jvm.tasks.Jar>("desktopJar")
    val desktopRuntimeClasspath = configurations.named("desktopRuntimeClasspath")

    dependsOn(desktopJarTask)

    archiveBaseName.set("ReqLab")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("distribute"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = mainClassName
    }

    // Include compiled classes/resources from the desktop jar.
    from(desktopJarTask.map { zipTree(it.archiveFile.get().asFile) })

    // Include all runtime dependency jars so the artifact is self-contained.
    from(
        desktopRuntimeClasspath.map { files ->
            files
                .filter { it.name.endsWith(".jar") }
                .map { zipTree(it) }
        }
    )

    // Drop signature metadata that becomes invalid when jars are merged.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    description = "Builds a runnable fat JAR at build/distribute/ReqLab-{version}.jar"
    group = "distribution"
}

tasks.register<Exec>("runReqLabJar") {
    dependsOn("packageReqLabJar")
    val jarFile = layout.buildDirectory.file("distribute/ReqLab-${project.version}.jar")
    val iconFile = project.file("src/desktopMain/resources/icons/reqlab-icon-256.png")
    val isMac = System.getProperty("os.name").lowercase().contains("mac")

    workingDir = project.rootDir
    val cmd = mutableListOf("java")
    if (isMac) {
        cmd += "-Xdock:name=ReqLab"
        cmd += "-Xdock:icon=${iconFile.absolutePath}"
    }
    cmd += "-jar"
    cmd += jarFile.get().asFile.absolutePath
    commandLine(cmd)
    description = "Runs the packaged ReqLab fat JAR (macOS uses app dock icon/name)."
    group = "application"
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        jvmArgs(
            "-Dapple.awt.application.name=ReqLab",
            "-Djava.awt.application.name=ReqLab",
            "-Xdock:name=ReqLab",
        )
    }
}
