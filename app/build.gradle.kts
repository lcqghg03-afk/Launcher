plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.example.tvlauncher"
    compileSdk = 34
    // minSdk 21 + targetSdk 28: chạy được trên cả TV Xiaomi nội địa đời cũ (Android 6-9)
    // và giữ quyền đọc USB/bộ nhớ đơn giản, không bị giới hạn bởi scoped storage
    defaultConfig { applicationId = "com.example.tvlauncher"; minSdk = 21; targetSdk = 28; versionCode = 3; versionName = "1.2" }
    signingConfigs {
        create("fixed") {
            storeFile = file("release.jks"); storePassword = "tvlauncher"; keyAlias = "tvlauncher"; keyPassword = "tvlauncher"
        }
    }
    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("fixed") }
        debug { signingConfig = signingConfigs.getByName("fixed") }
    }
    kotlinOptions { jvmTarget = "17" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
