package com.openim.tophone.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

public class DeviceUtils {
    @SuppressLint("HardwareIds")
    public static String getAndroidId(Context context) {
        // 1. 检查上下文是否为空
        if (context == null) {
            return "unknown_device_id"; // 返回默认值避免空指针
        }

        // 2. 安全获取 AndroidId，处理可能的 null 返回
        String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        // 3. 如果获取失败（返回 null 或空字符串），返回默认值
        if (TextUtils.isEmpty(androidId)) {
            return "unknown_device_id";
        }

        return androidId;
    }
}