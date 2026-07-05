plugins {
    alias(libs.plugins.android.application)
}
android {
    signingConfigs {
        create("release") {
            storeFile = file(project.property("MYAPP_STORE_FILE") as String)
            storePassword = project.property("MYAPP_STORE_PASSWORD") as String
            keyAlias = project.property("MYAPP_KEY_ALIAS") as String
            keyPassword = project.property("MYAPP_KEY_PASSWORD") as String
        }
    }
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
    buildFeatures {
        buildConfig = true
    }
    namespace = "com.openim.tophone"


    defaultConfig {
        applicationId = "com.openim.tophone"
        minSdk     = 26
        targetSdk  = 32
        compileSdk = 33
        versionCode = 300
        versionName = "3.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val devUseLocal = (project.findProperty("DEV_USE_LOCAL") as String?) ?: "true"
        val devLanHost = (project.findProperty("DEV_LAN_HOST") as String?) ?: "192.168.100.126"
        val devHttpPort = (project.findProperty("DEV_HTTP_PORT") as String?) ?: "8081"
        val devMqttPort = (project.findProperty("DEV_MQTT_PORT") as String?) ?: "1883"
        val remoteApiHost = (project.findProperty("REMOTE_API_HOST") as String?) ?: "api.tophone.cc"
        buildConfigField("boolean", "DEV_USE_LOCAL", devUseLocal)
        buildConfigField("String", "DEV_LAN_HOST", "\"$devLanHost\"")
        buildConfigField("int", "DEV_HTTP_PORT", devHttpPort)
        buildConfigField("int", "DEV_MQTT_PORT", devMqttPort)
        buildConfigField("String", "REMOTE_API_HOST", "\"$remoteApiHost\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "DEV_USE_LOCAL", "false")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            // 不要打 universal 包（会把两套 so 都打进去，体积可达 200MB+）
            isUniversalApk = false
        }
    }
    packaging {
        jniLibs {
            // 语聊房仅音频，剔除 RTC 视频/特效/文件播放等可选插件（见火山文档按需集成）
            val rtcOptionalPlugins = listOf(
                "libbytertc_vp8codec_extension.so",
                "libbytertc_videosr_extension.so",
                "libbytertc_videodenoise_extension.so",
                "libbytertc_videosharpen_extension.so",
                "libh265enc.so",
                "libbytertc_ffmpeg_audio_extension.so",
                "libbdaudioeffect.so",
                "libbmf_hydra.so",
                "libbmf_mods.so",
                "libbytenn.so",
            )
            val abis = listOf("arm64-v8a", "armeabi-v7a")
            excludes += abis.flatMap { abi ->
                rtcOptionalPlugins.map { plugin -> "lib/$abi/$plugin" }
            }
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
    exclude(group = "com.bytedanceapi", module = "ttsdk-ttbmf")
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.zxing:core:3.5.3")
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
    implementation(libs.volcengine.rtc)
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    // 官方 1.1.1 在 Android 12+ PendingIntent 会崩溃，用维护分支
    implementation("com.github.hannesa2:paho.mqtt.android:3.6.4")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
}
