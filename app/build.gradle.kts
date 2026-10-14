plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.optcg.foldmeta"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.optcg.foldmeta"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "4.0-CompleteMaster"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.window:window:1.3.0")

    // Coil for loading & caching official One Piece TCG card images
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Coroutines for live tournament JSON feed fetching
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
