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

    // ✅ Official RootEncoder 2.6.7 (confirmed working on JitPack)
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.7")

    // Optional: only needed if you use CameraXSource or CameraUvcSource
    // implementation("com.github.pedroSG94.RootEncoder:extra-sources:2.6.7")
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