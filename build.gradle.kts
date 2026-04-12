plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}

// ─── Centralized version ─────────────────────────────────────────────────────
// Change the version only in gradle.properties: appVersion=x.y.z
val appVersion: String by project

allprojects {
    group = "com.reqlab"
    version = appVersion
}

// Force a single instance of every CodeMirror sub-package so that
// @codemirror/state instanceof checks pass in Node test environments.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension> {
        // Core — must match nested deps of @codemirror/language + @codemirror/view
        resolution("@codemirror/state",    "6.6.0")
        resolution("@codemirror/view",     "6.38.6")
        resolution("@codemirror/language", "6.11.3")
        resolution("@codemirror/commands", "6.8.0")
        resolution("@codemirror/lint",     "6.9.0")
        // Language packs
        resolution("@codemirror/lang-json",       "6.0.2")
        resolution("@codemirror/lang-xml",        "6.1.0")
        resolution("@codemirror/lang-html",       "6.4.9")
        resolution("@codemirror/lang-javascript", "6.2.3")
    }
}

// Convenience: start the ReqLab sample API server from the repo root.
// Usage: ./gradlew runServer
tasks.register("runServer") {
    group = "application"
    description = "Starts the ReqLab sample API server at http://localhost:8080"
    // dependsOn(":sample-server:run")
}
