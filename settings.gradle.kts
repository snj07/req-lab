rootProject.name = "ReqLab"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

include(
    ":core-model",
    ":core-network",
    ":core-storage",
    ":core-scripting",
    ":test-support",
    ":qa-tests",
    ":feature-requests",
    ":feature-collections",
    ":feature-history",
    ":feature-environments",
    ":ui-desktop",
    ":ui-android",
    ":ui-ios",
    ":ui-web",
    ":sample-server"
)
