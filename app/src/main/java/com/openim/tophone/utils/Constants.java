package com.openim.tophone.utils;

import android.text.TextUtils;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.openim.IM;

public class Constants {

    public static final String DEFAULT_HOST = "cfapi.flbxw.cn";
    private static final String APP_AUTH = "https://" + DEFAULT_HOST + "/chat/"; //10008
    private static final String IM_API = "https://" + DEFAULT_HOST + "/api/"; //10002
    private static final String IM_WS = "wss://" + DEFAULT_HOST + "/msg_gateway/"; //10001

//    public static final String DEFAULT_HOST = "10.0.2.2";
//    private static final String APP_AUTH = "http://" + DEFAULT_HOST + ":10008"; //10008
//    private static final String IM_API = "http://" + DEFAULT_HOST + ":10002"; //10002
//    private static final String IM_WS = "ws://" + DEFAULT_HOST + ":10001"; //10001

    private static final String GROUP_OWNER_KEY = "ownerUserID";


    public static String getGroupOwnerKey(){
        return GROUP_OWNER_KEY;
    }

    public static String getImApiUrl() {
        return IM_API;

    }


    public static String getAppAuthUrl() {
      return APP_AUTH;

    }

    public static String getImWsUrl() {
         return IM_WS;
    }


    //文件夹
    public static final String File_DIR = IM.getStorageDir() + "/file/";


}
