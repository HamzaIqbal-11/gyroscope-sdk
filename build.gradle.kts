// Root build.gradle.kts for SDK project
// The allprojects block ensures JitPack can resolve dependencies
// even if dependencyResolutionManagement doesn't work on JitPack's build server

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}