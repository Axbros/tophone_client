package com.openim.tophone.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.openim.tophone.openim.entity.MobileDeviceProfile;

import java.util.List;
import java.util.Random;

public class DeviceUtils {
    private static final Random RANDOM = new Random();

    @SuppressLint("HardwareIds")
    public static String getAndroidId(Context context) {
        if (context == null) {
            return "";
        }

        String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        if (TextUtils.isEmpty(androidId)) {
            return "";
        }

        return androidId;
    }

    /** 优先 ANDROID_ID；拿不到则使用持久化的 8 位纯数字 ID */
    public static String getOrCreateClientDeviceId(Context context) {
        String androidId = getAndroidId(context);
        if (!TextUtils.isEmpty(androidId)) {
            return androidId;
        }
        SharedPreferences sp = context.getSharedPreferences(
                Constants.getSharedPrefsKeys_FILE_NAME(),
                Context.MODE_PRIVATE
        );
        String persisted = sp.getString(Constants.getPersistedClientDeviceIdKey(), "");
        if (!TextUtils.isEmpty(persisted)) {
            return persisted;
        }
        String generated = generateEightDigitId();
        sp.edit().putString(Constants.getPersistedClientDeviceIdKey(), generated).apply();
        return generated;
    }

    private static String generateEightDigitId() {
        int n = 10_000_000 + RANDOM.nextInt(90_000_000);
        return String.valueOf(n);
    }

    /** 采集设备指纹用于上报 */
    @SuppressLint({"HardwareIds", "MissingPermission"})
    public static MobileDeviceProfile collectProfile(Context context, String deviceCode) {
        MobileDeviceProfile p = new MobileDeviceProfile();
        p.clientDeviceId = getOrCreateClientDeviceId(context);
        p.deviceCode = deviceCode != null ? deviceCode : "";
        p.deviceModel = Build.MODEL != null ? Build.MODEL : "";
        p.deviceBrand = Build.BRAND != null ? Build.BRAND : "";
        p.osName = "android";
        p.osVersion = Build.VERSION.RELEASE != null ? Build.VERSION.RELEASE : "";
        p.appVersion = AppVersionUtil.getVersionName(context);
        p.phoneNumber = resolvePhoneNumber(context);
        return p;
    }

    @SuppressLint("MissingPermission")
    static String resolvePhoneNumber(Context context) {
        if (context == null) {
            return "";
        }
        String number = "";
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                number = tm.getLine1Number();
            }
        } catch (SecurityException ignored) {
            // READ_PHONE_STATE / READ_PHONE_NUMBERS 未授权
        }
        if (!TextUtils.isEmpty(number)) {
            return normalizePhone(number);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (sm != null) {
                    List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
                    if (subs != null) {
                        for (SubscriptionInfo info : subs) {
                            if (info == null) continue;
                            CharSequence line = info.getNumber();
                            if (!TextUtils.isEmpty(line)) {
                                return normalizePhone(line.toString());
                            }
                        }
                    }
                }
            } catch (SecurityException ignored) {
            }
        }
        return "";
    }

    private static String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }
}
