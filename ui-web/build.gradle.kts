plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(compose.html.core)
            implementation(compose.runtime)
            implementation(project(":feature-requests"))
            implementation(project(":feature-collections"))
            implementation(project(":feature-history"))
            implementation(project(":feature-environments"))
        }
    }
}
