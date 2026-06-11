# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# VolcEngine RTC
-keep class com.ss.** { *; }
-keep class com.bytedance.** { *; }
-keep class com.bytertc.** { *; }
-keep class com.pandora.** { *; }

# MQTT (Paho)
-keep class open_im_sdk.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson models (public fields must keep names for JSON mapping in release)
-keep class com.openim.tophone.openim.entity.** { *; }
-keep interface com.openim.tophone.repository.** { *; }

# Retrofit / RxJava
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn io.reactivex.**

# Eclipse Paho MQTT
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**
-keep class info.mqtt.android.service.** { *; }
-dontwarn info.mqtt.android.service.**