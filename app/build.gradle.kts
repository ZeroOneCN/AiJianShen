plugins {
    id("com.android.application")
}

android {
    namespace = "com.yuanlian.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yuanlian.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.8.2"
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
