package com.openim.tophone.utils;

/**
 * 支持动态更新域名的 Constants 类
 */
public class Constants {

    private static final String SharedPrefsKeys_FILE_NAME = "SharedPrefsKeys";
    private static final String SharedPrefsKeys_NICKNAME = "NICKNAME";

    /**
     * 电脑局域网 IP：Mac 执行 ifconfig | grep "inet " 查看（当前网段示例 192.168.100.x）。
     * Android 模拟器访问本机服务请改为 "10.0.2.2"（模拟器专用，指向宿主机 localhost）。
     * 真机与电脑同一 WiFi 时用电脑的局域网 IP，不要用 127.0.0.1。
     */
    public static final String LOCAL_LAN_HOST = "10.0.2.2";

    /** true：check_version / 打卡 / MQTT 凭证 → http://LOCAL_LAN_HOST:8081 */
    public static final boolean USE_LOCAL_LAN = true;

    private static final String REMOTE_HOST = "api.flbxw.cn";

    private static String fileDir;

    public static final String DEFAULT_HOST = USE_LOCAL_LAN ? LOCAL_LAN_HOST : REMOTE_HOST;

    private static String CURRENT_HOST = DEFAULT_HOST;

    private static String apiHost() {
        return USE_LOCAL_LAN ? LOCAL_LAN_HOST : CURRENT_HOST;
    }

    public static void initFileDir(String appFilesPath) {
        fileDir = appFilesPath + "/file/";
    }

    /** Retrofit baseUrl，末尾必须有 / */
    public static String getManagementUrl() {
        if (USE_LOCAL_LAN) {
            return "http://" + LOCAL_LAN_HOST + ":8081/";
        }
        return "https://" + CURRENT_HOST + "/api-management/";
    }

    public static String RTC_APP_ID = "";

    public static String getRtcManagementBase() {
        if (USE_LOCAL_LAN) {
            return "http://" + LOCAL_LAN_HOST + ":8081";
        }
        return "https://" + CURRENT_HOST + "/api-management";
    }

    public static String getVerifyRoomURL() {
        return getRtcManagementBase() + "/api/v1/record/verifyRoom";
    }

    public static String getRtcConfigURL() {
        return getRtcManagementBase() + "/api/v1/config/tophone_world";
    }

    public static String getNotifyRoomManagerURL() {
        return getRtcManagementBase() + "/api/v1/record/notifyRoomManager";
    }

    public static void updateHost(String host) {
        CURRENT_HOST = host;
    }

    private static final String GROUP_OWNER_KEY = "ownerUserID";
    private static final String GROUP_NAME = "groupName";
    private static final String ASSIGNED_ROOM_ID_KEY = "assignedRoomID";
    private static final String CHECKED_IN_KEY = "checkedIn";

    private static final boolean USE_MQTT = true;

    public static String getMqttBrokerTcp() {
        return USE_LOCAL_LAN
                ? ("tcp://" + LOCAL_LAN_HOST + ":1883")
                : "ssl://api.flbxw.cn:8883";
    }

    public static boolean isUseMqtt() {
        return USE_MQTT;
    }

    public static String getGroupOwnerKey() { return GROUP_OWNER_KEY; }

    public static String getGroupName() { return GROUP_NAME; }

    public static String getAssignedRoomIdKey() { return ASSIGNED_ROOM_ID_KEY; }

    public static String getCheckedInKey() { return CHECKED_IN_KEY; }

    public static String getFileDir() {
        return fileDir != null ? fileDir : "";
    }

    public static String getSharedPrefsKeys_FILE_NAME(){
        return SharedPrefsKeys_FILE_NAME;
    }

    public static String getSharedPrefsKeys_NICKNAME(){
        return SharedPrefsKeys_NICKNAME;
    }
}
