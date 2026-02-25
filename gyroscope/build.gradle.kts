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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
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

    // This should now resolve correctly because JitPack is first in settings
    implementation("com.github.BilalFarooq05:RootEncoder:v2.6.7-audio-mix")
    implementation("com.github.BilalFarooq05.RootEncoder:extra-sources:v2.6.7-audio-mix")

    // Uncomment only if you actually need special camera sources
    // implementation("com.github.pedroSG94.RootEncoder:extra-sources:2.6.7")

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
                // version is injected by JitPack via -Pversion=...
            }
        }
    }
}