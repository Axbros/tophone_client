package com.openim.tophone.utils;

import android.text.TextUtils;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.openim.IM;

public class Constants {

//    public static final String DEFAULT_HOST = "cfapi.flbxw.cn";
//    private static final String APP_AUTH = "https://" + DEFAULT_HOST + "/chat/"; //10008
//    private static final String IM_API = "https://" + DEFAULT_HOST + "/api/"; //10002
//    private static final String IM_WS = "wss://" + DEFAULT_HOST + "/msg_gateway/"; //10001

    public static final String DEFAULT_HOST = "10.0.2.2";
    private static final String APP_AUTH = "http://" + DEFAULT_HOST + ":10008"; //10008
    private static final String IM_API = "http://" + DEFAULT_HOST + ":10002"; //10002
    private static final String IM_WS = "ws://" + DEFAULT_HOST + ":10001"; //10001



    public static String getImApiUrl() {
        String url = SharedPreferencesUtil.get(BaseApp.inst()).getString("IM_API_URL");
        if (TextUtils.isEmpty(url)) return IM_API;
        return url;
    }


    public static String getAppAuthUrl() {
        String url = SharedPreferencesUtil.get(BaseApp.inst()).getString("APP_AUTH_URL");
        if (TextUtils.isEmpty(url)) return APP_AUTH;
        return url;
    }

    public static String getImWsUrl() {
        String url = SharedPreferencesUtil.get(BaseApp.inst()).getString("IM_WS_URL");
        if (TextUtils.isEmpty(url)) return IM_WS;
        return url;
    }


    //文件夹
    public static final String File_DIR = IM.getStorageDir() + "/file/";




    //日誌級別
    public static final String K_LOG_LEVEL = "logLevel";
    // 阅后即焚存储标识

}
