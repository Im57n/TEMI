plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.temiapp"

    // 【修改 1】 改成 34 (Android 14) 穩定版，避免用 36 (預覽版) 導致錯誤
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.temiapp"
        minSdk = 23 // Temi 支援的最低版本，不用動

        // 【修改 2】 配合上面，這裡也改成 34
        targetSdk = 34

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.belerweb:pinyin4j:2.5.1")
    // 這行版本 1.136.0 是正確的，保留即可
    implementation("com.robotemi:sdk:1.136.0")
}