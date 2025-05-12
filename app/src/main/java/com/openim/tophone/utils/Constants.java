package com.openim.tophone.utils;

import android.text.TextUtils;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.openim.IM;

/**
 * 常量类，包含一些常用的 URL 和键名等常量
 */
public class Constants {
    // 是否为本地环境的标识

    private static final String SharedPrefsKeys_FILE_NAME = "SharedPrefsKeys";

    private static final String SharedPrefsKeys_NICKNAME = "NICKNAME";
    private static final boolean IS_LOCAL_ENV = false;
    public static final String DB_NAME_USERID="userId";
    public static final String DB_NAME_NICKNAME="nickName";

    public static final String DB_NAME_EMAIL="email";

    private static final String FILE_DIR = IM.getStorageDir() + "/file/";

    private static final String LOG_FILE_PATH = FILE_DIR+"tophone.log";

    // 默认主机地址
    public static final String DEFAULT_HOST = IS_LOCAL_ENV ? "192.168.50.91" : "api.ndvfp.cn";

    // APP 认证 URL
    private static final String APP_AUTH = (IS_LOCAL_ENV ? "http://" : "https://") + DEFAULT_HOST + (IS_LOCAL_ENV ? ":10008" : "/chat/");

    // IM API URL
    private static final String IM_API = (IS_LOCAL_ENV ? "http://" : "https://") + DEFAULT_HOST + (IS_LOCAL_ENV ? ":10002" : "/api/");

    // IM WebSocket URL
    private static final String IM_WS = (IS_LOCAL_ENV ? "ws://" : "wss://") + DEFAULT_HOST + (IS_LOCAL_ENV ? ":10001" : "/msg_gateway");

    // 群组所有者键名
    private static final String GROUP_OWNER_KEY = "ownerUserID";

    /**
     * 获取群组所有者键名
     *
     * @return 群组所有者键名
     */
    public static String getGroupOwnerKey() {
        return GROUP_OWNER_KEY;
    }

    /**
     * 获取 IM API 的 URL
     *
     * @return IM API 的 URL
     */
    public static String getImApiUrl() {
        return IM_API;
    }

    /**
     * 获取 APP 认证的 URL
     *
     * @return APP 认证的 URL
     */
    public static String getAppAuthUrl() {
        return APP_AUTH;
    }

    /**
     * 获取 IM WebSocket 的 URL
     *
     * @return IM WebSocket 的 URL
     */
    public static String getImWsUrl() {
        return IM_WS;
    }

    // 文件存储目录
    public static String getFileDir(){
        return FILE_DIR;
    }


    public static String getSharedPrefsKeys_FILE_NAME(){
        return SharedPrefsKeys_FILE_NAME;
    }


    public static String getSharedPrefsKeys_NICKNAME(){
        return SharedPrefsKeys_NICKNAME;
    }
}