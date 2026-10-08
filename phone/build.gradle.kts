plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.notifytv.phone"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.notifytv.phone"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "5.3"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/notifytv.p12")
            storePassword = "notifytv"
            keyAlias = "notifytv"
            keyPassword = "notifytv"
            storeType = "pkcs12"
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
