plugins {
    alias(libs.plugins.android.application)
}

android {

    dataBinding{
        enable = true
    }
    viewBinding{
        enable = true
    }
    namespace = "com.openim.tophone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.openim.tophone"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk= true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
    implementation(libs.buildinfrastructure)
    implementation(libs.logging.interceptor)
    implementation(libs.adapter.rxjava2)
    implementation(libs.converter.gson)
    implementation(libs.fastjson)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)


}