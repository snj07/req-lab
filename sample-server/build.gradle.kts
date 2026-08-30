plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.reqlab.server.ApplicationKt")
}

dependencies {
    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Serialization
    implementation(libs.serialization.json)

    // Logging (required by Netty)
    implementation("ch.qos.logback:logback-classic:1.5.13")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
}

tasks.test {
    useJUnit()
}

tasks.named<JavaExec>("run") {
    // Allow the server to read from stdin (useful for manual stop)
    standardInput = System.`in`
}

// Convenience alias: ./gradlew :sample-server:runServer
tasks.register("runServer") {
    group = "application"
    description = "Starts the ReqLab sample API server at http://localhost:8080"
    dependsOn("run")
}

/**
 * Writes ~/.local/bin/sample-server (macOS/Linux) so the mock is on the login PATH
 * as an MCP stdio process. The HTTP server is still `./gradlew :sample-server:run`.
 */
tasks.register<JavaExec>("installMcpCommand") {
    group = "application"
    description = "Installs `sample-server` on PATH as an MCP stdio server"
    dependsOn("installDist", "classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.reqlab.server.InstallMcpCommandKt")
    val unixLauncher = layout.projectDirectory.file("mcp-stdio")
    val windowsLauncher = layout.buildDirectory.file("install/sample-server/bin/sample-server.bat")
    args(unixLauncher.asFile.absolutePath, windowsLauncher.get().asFile.absolutePath)
}
