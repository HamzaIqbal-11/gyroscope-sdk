plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
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

// ✅ LocalBroadcastManager add kiya
dependencies {
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.core:core-ktx:1.12.0")
    // Recommended: Use the full RootEncoder library (includes RTMP + RTSP + more)
    implementation ("com.github.pedroSG94:RootEncoder:library:2.7.7")   // Check latest tag on GitHub

    // OR if you only need RTMP part (smaller size):
    // implementation 'com.github.pedroSG94.RootEncoder:rtmp:2.7.7'

    // Older name (still works for compatibility):
    // implementation 'com.github.pedroSG94.rtmp-rtsp-stream-client-java:rtplibrary:2.7.7'
}

afterEvaluate {
    extensions.configure<PublishingExtension>("publishing") {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.HamzaIqbal-11"
                artifactId = "gyroscope-sdk"
                version = "1.0.7"
            }
        }
    }
}