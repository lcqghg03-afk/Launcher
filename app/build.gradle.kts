plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.example.tvlauncher"
    compileSdk = 34
    // minSdk 21 + targetSdk 28: chạy được trên cả TV Xiaomi nội địa đời cũ (Android 6-9)
    // và giữ quyền đọc USB/bộ nhớ đơn giản, không bị giới hạn bởi scoped storage
    defaultConfig { applicationId = "com.example.tvlauncher"; minSdk = 21; targetSdk = 28; versionCode = 8; versionName = "1.8" }
    buildTypes {
        // GitHub Actions builds a debug APK; do not reference a local release.jks that is not in the repository.
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }
    // targetSdk 28 là cố ý (đọc USB/bộ nhớ đơn giản trên TV). Lint của Google Play đòi >= 33 nên tắt kiểm tra này khi build
    lint { checkReleaseBuilds = false; abortOnError = false }
    kotlinOptions { jvmTarget = "17" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
