plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":editor-core"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
