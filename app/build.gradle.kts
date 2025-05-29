plugins {
    alias(libs.plugins.android.application)
}
android {
    lint {
        disable += "ExpiredTargetSdkVersion" // 忽略目标 SDK 版本错误
        abortOnError = false // 遇到其他错误时不终止构建
    }
    dataBinding{
        enable = true
    }
    viewBinding{
        enable = true
    }
    namespace = "com.openim.tophone"


    defaultConfig {
        applicationId = "com.openim.tophone"
        minSdk     = 26
        targetSdk  = 32
        compileSdk = 33
        versionCode = 121
        versionName = "1.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits{
        abi{
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk= true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
configurations.all {
    resolutionStrategy {
        force("androidx.activity:activity:1.7.2")
    }
}

dependencies {
    implementation(libs.android.sdk)
    implementation(libs.core.sdk)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.fastjson2)
    implementation(libs.logging.interceptor)
    implementation(libs.adapter.rxjava2)
    implementation(libs.converter.gson)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}