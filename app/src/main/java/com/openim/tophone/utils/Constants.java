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
    private static String CURRENT_API_BASE_URL = USE_LOCAL_LAN ? getLocalManagementBase() : "https://" + REMOTE_HOST;
    private static String CURRENT_RTC_BASE_URL = CURRENT_API_BASE_URL;
    private static String CURRENT_MQTT_BROKER_TCP = "";

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
        return trimTrailingSlash(CURRENT_API_BASE_URL) + "/";
    }

    public static String RTC_APP_ID = "";

    public static String getRtcManagementBase() {
        if (USE_LOCAL_LAN) {
            return getLocalManagementBase();
        }
        return trimTrailingSlash(CURRENT_RTC_BASE_URL);
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

    /** Legacy root ping; available before /api/v1/ping is deployed. */
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
        CURRENT_API_BASE_URL = "https://" + REMOTE_HOST;
        CURRENT_RTC_BASE_URL = CURRENT_API_BASE_URL;
        CURRENT_MQTT_BROKER_TCP = "";
    }

    public static void updateHost(String host) {
        String normalized = ServerEndpointHelper.normalizeHost(host);
        if (!normalized.isEmpty()) {
            CURRENT_HOST = normalized;
            CURRENT_API_BASE_URL = "https://" + normalized;
            CURRENT_RTC_BASE_URL = CURRENT_API_BASE_URL;
        }
    }

    public static void updateApiBaseUrl(String apiBaseUrl) {
        String normalized = normalizeBaseUrl(apiBaseUrl);
        if (normalized.isEmpty()) {
            return;
        }
        CURRENT_API_BASE_URL = normalized;
        String host = extractHost(normalized);
        if (!host.isEmpty()) {
            CURRENT_HOST = host;
        }
    }

    public static void updateRtcBaseUrl(String rtcBaseUrl) {
        String normalized = normalizeBaseUrl(rtcBaseUrl);
        CURRENT_RTC_BASE_URL = normalized.isEmpty() ? CURRENT_API_BASE_URL : normalized;
    }

    public static void updateMqttBrokerTcp(String brokerTcp) {
        CURRENT_MQTT_BROKER_TCP = brokerTcp != null ? brokerTcp.trim() : "";
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
        if (CURRENT_MQTT_BROKER_TCP != null && !CURRENT_MQTT_BROKER_TCP.isEmpty()) {
            return CURRENT_MQTT_BROKER_TCP;
        }
        return "wss://" + CURRENT_HOST + "/mqtt";
    }

    public static String getBootstrapUrl() {
        return "https://" + BuildConfig.BOOTSTRAP_HOST + "/api/v1/app/bootstrap";
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null) {
            return "";
        }
        String out = trimTrailingSlash(url.trim());
        if (out.startsWith("https://") || out.startsWith("http://")) {
            return out;
        }
        return "";
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String out = url.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static String extractHost(String baseUrl) {
        String host = baseUrl;
        if (host.startsWith("https://")) {
            host = host.substring(8);
        } else if (host.startsWith("http://")) {
            host = host.substring(7);
        }
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        return host.trim();
    }

    public static boolean isUseMqtt() {
        return USE_MQTT;
    }

    public static String getGroupOwnerKey() { return GROUP_OWNER_KEY; }

    public static String getGroupName() { return GROUP_NAME; }

    public static String getAssignedRoomIDKey() { return ASSIGNED_ROOM_ID_KEY; }

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
