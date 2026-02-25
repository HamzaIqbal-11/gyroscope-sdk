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

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }   // ← yeh bohot zaroori hai
}

dependencies {
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.core:core-ktx:1.13.1")   // thoda updated version

    // Pedro RTMP/RTSP library – latest stable version jitpack ke hisaab se
    implementation("com.github.pedroSG94.RootEncoder:library:2.7.7")

    // Agar sirf RTMP chahiye to yeh bhi try kar sakte ho (smaller size)
    // implementation("com.github.pedroSG94.RootEncoder:rtmp:2.7.7")
}

// Publishing block – version hard-code mat karo, jitpack tag se lega
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.HamzaIqbal-11"
                artifactId = "gyroscope-sdk"
                // version = "1.0.7"   ← yeh line comment out ya hata do
            }
        }
    }
}