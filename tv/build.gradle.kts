plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.example.notifytv.tv"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.notifytv.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 5
        versionName = "5.0"
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
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.zxing:core:3.5.3")
}
