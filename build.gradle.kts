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

// Convenience: start the ReqLab sample API server from the repo root.
// Usage: ./gradlew runServer
tasks.register("runServer") {
    group = "application"
    description = "Starts the ReqLab sample API server at http://localhost:8080"
    dependsOn(":sample-server:run")
}
