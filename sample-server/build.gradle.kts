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
