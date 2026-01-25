package com.openim.tophone.utils;

import com.openim.tophone.openim.IM;

/**
 * 支持动态更新域名的 Constants 类
 */
public class Constants {

    private static final String SharedPrefsKeys_FILE_NAME = "SharedPrefsKeys";
    private static final String SharedPrefsKeys_NICKNAME = "NICKNAME";
    private static final boolean IS_LOCAL_ENV = false;

    private static final String FILE_DIR = IM.getStorageDir() + "/file/";

    // 默认 host（第一次启动使用）
    public static final String DEFAULT_HOST = IS_LOCAL_ENV ? "192.168.50.91" : "apiv2.uc0.cn";

    // 当前生效的 host（可被动态更新）
    private static String CURRENT_HOST = DEFAULT_HOST;

    // ======== ⭐ 动态 URL(实时计算) 而不是写死的 final ⭐ ========//
    public static String getAppAuthUrl() {
        return (IS_LOCAL_ENV ? "http://" : "https://") +
                CURRENT_HOST +
                (IS_LOCAL_ENV ? ":10008" : "/chat/");
    }

    public static String getManagementUrl() {
        return (IS_LOCAL_ENV ? "http://" : "https://") +
                CURRENT_HOST +
                (IS_LOCAL_ENV ? ":8080" : "/api-management/");
    }

    public static String getImApiUrl() {
        return (IS_LOCAL_ENV ? "http://" : "https://") +
                CURRENT_HOST +
                (IS_LOCAL_ENV ? ":10002" : "/api");
    }

    public static String getImWsUrl() {
        return (IS_LOCAL_ENV ? "ws://" : "wss://") +
                CURRENT_HOST +
                (IS_LOCAL_ENV ? ":10001" : "/msg_gateway");
    }

    // ======== ⭐ 对外暴露的 host 更新方法 ⭐ ======== //
    public static void updateHost(String host) {
        CURRENT_HOST = host;
    }

    // ======== 其它常量保持不变 ======== //

    private static final String GROUP_OWNER_KEY = "ownerUserID";
    private static final String GROUP_NAME = "groupName";

    public static String getGroupOwnerKey() { return GROUP_OWNER_KEY; }

    public static String getGroupName() { return GROUP_NAME; }

    public static String getFileDir(){ return FILE_DIR; }

    public static String getSharedPrefsKeys_FILE_NAME(){
        return SharedPrefsKeys_FILE_NAME;
    }

    public static String getSharedPrefsKeys_NICKNAME(){
        return SharedPrefsKeys_NICKNAME;
    }
}
