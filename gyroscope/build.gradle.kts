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

        // If you're publishing → version is usually set via -Pversion=... on command line (JitPack style)
        // version = "1.0.13"   // ← usually set externally

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    // Optional: if you're publishing AARs with sources
    publishing {
        singleVariant("release") {
            withSourcesJar()
            // withJavadocJar()   // if you want javadoc too
        }
    }
}

dependencies {
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.core:core-ktx:1.13.1")

    // The critical dependency — should now resolve with JitPack first
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.6")

    // If you need camera2 / external sources (uncomment if actually used)
    // implementation("com.github.pedroSG94.RootEncoder:extra-sources:2.6.7")

    // Firebase (only if you're really using direct boot messaging — quite old version)
    implementation("com.google.firebase:firebase-messaging-directboot:20.2.0")

    // Emoji support
    implementation("androidx.emoji2:emoji2:1.5.0")
    implementation("androidx.emoji2:emoji2-bundled:1.5.0")

    // Desugaring (very useful with Java 17 + old minSdk)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}

// Optional: helps when publishing to local maven / JitPack
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.HamzaIqbal-11"
                artifactId = "gyroscope-sdk"
                // version is usually injected by JitPack via -Pversion=...
            }
        }
    }
}