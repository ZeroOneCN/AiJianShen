plugins {
    id("com.android.application")
}

android {
    namespace = "com.aijianshen.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aijianshen.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.9"
    }

    androidResources {
        // 动态 WebP 需要可随机访问，不能被 aapt 压缩
        noCompress += listOf("webp", "jpg")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
