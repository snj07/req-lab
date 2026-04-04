plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    jvmToolchain(21)

    sourceSets {
        jvmTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(project(":core-model"))
                implementation(project(":core-scripting"))
                implementation(project(":core-network"))
                implementation(project(":feature-requests"))
                implementation(project(":sample-server"))
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
                implementation(libs.kotlinx.datetime)
                implementation(libs.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.junit4)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
