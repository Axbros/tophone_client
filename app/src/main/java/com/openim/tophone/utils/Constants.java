package com.openim.tophone.utils;

import android.content.Context;
import android.os.Build;

import com.openim.tophone.BuildConfig;

/**
 * 服务器地址统一由此类解析。
 * 本地开发只需改 gradle.properties 里的 DEV_LAN_HOST（真机局域网 IP），模拟器会自动用 10.0.2.2。
 */
public class Constants {

    private static final String SharedPrefsKeys_FILE_NAME = "SharedPrefsKeys";
    private static final String SharedPrefsKeys_NICKNAME = "NICKNAME";
    private static final String NORMAL_USERNAME_KEY = "normalUsername";
    private static final String NORMAL_USER_ID_KEY = "normalUserID";
    private static final String PERSISTED_CLIENT_DEVICE_ID_KEY = "persistedClientDeviceId";

    /** true：走本地开发机；false：走线上 REMOTE_API_HOST */
    public static final boolean USE_LOCAL_LAN = BuildConfig.DEV_USE_LOCAL;

    private static final String REMOTE_HOST = BuildConfig.REMOTE_API_HOST;
    private static final String EMULATOR_HOST = "10.0.2.2";

    private static String fileDir;

    public static final String DEFAULT_HOST = USE_LOCAL_LAN ? resolveLocalHost() : REMOTE_HOST;

    private static String CURRENT_HOST = DEFAULT_HOST;

    /** 模拟器 → 10.0.2.2；真机 → gradle.properties 中的 DEV_LAN_HOST */
    public static String resolveLocalHost() {
        if (isEmulator()) {
            return EMULATOR_HOST;
        }
        return BuildConfig.DEV_LAN_HOST;
    }

    /** 固定返回模拟器地址，供线路探测等场景 */
    public static String getEmulatorManagementBase() {
        return "http://" + EMULATOR_HOST + ":" + BuildConfig.DEV_HTTP_PORT;
    }

    /** 固定返回局域网真机地址，供线路探测等场景 */
    public static String getLanDeviceManagementBase() {
        return "http://" + BuildConfig.DEV_LAN_HOST + ":" + BuildConfig.DEV_HTTP_PORT;
    }

    /** 当前运行环境实际使用的管理端 base（无末尾斜杠） */
    public static String getLocalManagementBase() {
        return "http://" + resolveLocalHost() + ":" + BuildConfig.DEV_HTTP_PORT;
    }

    public static boolean isEmulator() {
        String fingerprint = Build.FINGERPRINT;
        String model = Build.MODEL;
        String product = Build.PRODUCT;
        String hardware = Build.HARDWARE;
        String manufacturer = Build.MANUFACTURER;
        return fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86")
                || manufacturer.contains("Genymotion")
                || product.contains("sdk")
                || product.contains("emulator")
                || product.contains("simulator")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu");
    }

    public static void initFileDir(String appFilesPath) {
        fileDir = appFilesPath + "/file/";
    }

    /** Retrofit baseUrl，末尾必须有 / */
    public static String getManagementUrl() {
        if (USE_LOCAL_LAN) {
            return getLocalManagementBase() + "/";
        }
        return "https://" + CURRENT_HOST + "/";
    }

    public static String RTC_APP_ID = "";

    public static String getRtcManagementBase() {
        if (USE_LOCAL_LAN) {
            return getLocalManagementBase();
        }
        return "https://" + CURRENT_HOST;
    }

    public static String getVerifyRoomURL() {
        return getRtcManagementBase() + "/api/v1/record/verifyRoom";
    }

    public static String getRtcConfigURL() {
        return getRtcManagementBase() + "/api/v1/config/tophone_world";
    }

    /** Primary latency probe; falls back to {@link #getPingUrlFallback()} on HTTP 404. */
    public static String getPingUrl() {
        return getPingUrlPrimary();
    }

    public static String getPingUrlPrimary() {
        return getRtcManagementBase() + "/api/v1/ping";
    }

    /** Legacy root ping; available on api-v3 before /api/v1/ping is deployed. */
    public static String getPingUrlFallback() {
        return getRtcManagementBase() + "/ping";
    }

    public static String getCurrentHost() {
        return CURRENT_HOST;
    }

    public static String getBuiltInRemoteHost() {
        return REMOTE_HOST;
    }

    public static void resetToBuiltInHost() {
        CURRENT_HOST = REMOTE_HOST;
    }

    public static void updateHost(String host) {
        String normalized = ServerEndpointHelper.normalizeHost(host);
        if (!normalized.isEmpty()) {
            CURRENT_HOST = normalized;
        }
    }

    /**
     * Apply cached custom host from {@link DomainManager}, or fall back to built-in REMOTE_API_HOST.
     * In release / production mode, stale LAN IPs from debug are ignored and cleared.
     */
    public static void resolveHostFromStorage(Context context) {
        String cached = DomainManager.getHost(context);
        if (cached == null || cached.isEmpty()) {
            resetToBuiltInHost();
            return;
        }
        String normalized = ServerEndpointHelper.normalizeHost(cached);
        if (USE_LOCAL_LAN) {
            if (!normalized.isEmpty()) {
                updateHost(normalized);
            }
            return;
        }
        if (ServerEndpointHelper.isPrivateOrLocalHost(normalized)
                || !ServerEndpointHelper.isValidHost(normalized)) {
            DomainManager.clear(context);
            resetToBuiltInHost();
            return;
        }
        updateHost(normalized);
    }

    private static final String GROUP_OWNER_KEY = "ownerUserID";
    private static final String GROUP_NAME = "groupName";
    private static final String ASSIGNED_ROOM_ID_KEY = "assignedRoomID";
    private static final String CHECKED_IN_KEY = "checkedIn";
    private static final String VOICE_DISABLED_KEY = "voiceDisabled";
    private static final String SMS_DISABLED_KEY = "smsDisabled";
    private static final String DEVICE_POLICY_STATUS_KEY = "devicePolicyStatus";

    private static final boolean USE_MQTT = true;

    public static String getMqttBrokerTcp() {
        if (USE_LOCAL_LAN) {
            return "ws://" + resolveLocalHost() + ":8083/mqtt";
        }
        return "wss://" + CURRENT_HOST + "/mqtt";
    }

    public static boolean isUseMqtt() {
        return USE_MQTT;
    }

    public static String getGroupOwnerKey() { return GROUP_OWNER_KEY; }

    public static String getGroupName() { return GROUP_NAME; }

    public static String getAssignedRoomIdKey() { return ASSIGNED_ROOM_ID_KEY; }

    public static String getCheckedInKey() { return CHECKED_IN_KEY; }

    public static String getVoiceDisabledKey() { return VOICE_DISABLED_KEY; }

    public static String getSmsDisabledKey() { return SMS_DISABLED_KEY; }

    public static String getDevicePolicyStatusKey() { return DEVICE_POLICY_STATUS_KEY; }

    public static String getFileDir() {
        return fileDir != null ? fileDir : "";
    }

    public static String getSharedPrefsKeys_FILE_NAME(){
        return SharedPrefsKeys_FILE_NAME;
    }

    public static String getSharedPrefsKeys_NICKNAME(){
        return SharedPrefsKeys_NICKNAME;
    }

    public static String getNormalUsernameKey() {
        return NORMAL_USERNAME_KEY;
    }

    public static String getNormalUserIDKey() {
        return NORMAL_USER_ID_KEY;
    }

    public static String getPersistedClientDeviceIdKey() {
        return PERSISTED_CLIENT_DEVICE_ID_KEY;
    }
}
