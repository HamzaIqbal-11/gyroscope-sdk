plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.earnscape.gyroscopesdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.core:core-ktx:1.13.1")

    // Remove fork (causing mismatch)
    // implementation("com.github.BilalFarooq05:RootEncoder:v2.6.7-audio-mix")
    // implementation("com.github.BilalFarooq05.RootEncoder:extra-sources:v2.6.7-audio-mix")

    // Use official – this matches your connectChecker code perfectly
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.7")

    // Optional extra-sources if you use special cameras
    // implementation("com.github.pedroSG94.RootEncoder:extra-sources:2.6.7")

    // Your other deps...
    implementation("com.google.firebase:firebase-messaging-directboot:20.2.0")
    implementation("androidx.emoji2:emoji2:1.5.0")
    implementation("androidx.emoji2:emoji2-bundled:1.5.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.HamzaIqbal-11"
                artifactId = "gyroscope-sdk"
            }
        }
    }
}