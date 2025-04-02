plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.openim.tophone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.openim.tophone"
        minSdk = 24
        targetSdk = 35
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
}

dependencies {
    implementation("io.openim:android-sdk:3.8.3.2@aar")
    implementation("io.openim:core-sdk:3.8.3@aar")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.android.sdk)
    implementation(libs.library)
    implementation(libs.fastjson2)
    implementation(libs.buildinfrastructure)
    implementation(libs.rxjava)
    implementation(libs.logging.interceptor)
    implementation(libs.adapter.rxjava2)
    implementation(libs.converter.gson)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)


}