plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.example.gyroscope"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Sources JAR - fixed version
tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from("src/main/kotlin")
    // from("src/main/java")  // ← keep only if you have Java files
}

// Ensure artifacts include it (good to have)
artifacts {
    archives(tasks.named("sourcesJar"))
}

// Publishing block with explicit dependency fix
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                artifact(tasks.named("sourcesJar"))

                // Optional: group/artifact/version overrides (JitPack ignores version but it's fine)
                groupId = "com.github.HamzaIqbal-11"
                artifactId = "gyroscope-sdk"
            }
        }
        repositories {
            mavenLocal()
        }
    }

    // Critical fix: Make the metadata generation task depend on sourcesJar
    // This declares the explicit dependency Gradle wants
    tasks.named("generateMetadataFileForReleasePublication") {
        dependsOn("sourcesJar")
    }
}