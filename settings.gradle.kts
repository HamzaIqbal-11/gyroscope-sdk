pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_PROJECT ensures allprojects{} repos in build.gradle also work
    // This is important for JitPack compatibility
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "sdk"
include(":gyroscope")

// Helps with Java 17 auto-provisioning (mostly for local builds)
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}