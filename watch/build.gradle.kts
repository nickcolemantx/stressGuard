plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.stressguard.watch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.stressguard.watch"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(project(":shared"))

    // Wear OS Compose
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")
    implementation("androidx.compose.ui:ui:1.6.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.1")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Samsung Health SDKs (drop the .aar files in /libs — see libs/README.md)
    implementation(files("../libs/samsung-health-sensor-sdk.aar"))
    implementation(files("../libs/samsung-health-data-sdk.aar"))

    // Wearable Data Layer (Watch <-> Phone)
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Background service + coroutines
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")
    implementation("androidx.core:core-ktx:1.12.0")

    // Local database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Settings persistence
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // JSON for Wearable Data Layer payloads
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Wear Tile + ProtoLayout (tiles artifact does not transitively expose protolayout API)
    implementation("androidx.wear.tiles:tiles:1.3.0")
    implementation("androidx.wear.tiles:tiles-material:1.3.0")
    implementation("androidx.wear.protolayout:protolayout:1.1.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.1.0")
    implementation("androidx.wear.protolayout:protolayout-expression:1.1.0")
    implementation("com.google.guava:guava:32.1.3-android")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.6.1")
}
