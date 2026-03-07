plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    jvmToolchain(21)

    sourceSets {
        jvmTest.dependencies {
            implementation(project(":core-model"))
            implementation(project(":core-network"))
            implementation(project(":feature-requests"))
            implementation(project(":test-support"))
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.cio)
            implementation(libs.junit4)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
