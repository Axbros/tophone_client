package com.openim.tophone.openim;

import android.app.Application;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.L;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.listener.OnFriendshipListener;
import io.openim.android.sdk.models.FriendApplicationInfo;
import io.openim.android.sdk.models.InitConfig;

public class IM {
    private static final String TAG="APP:IM.java";
    public static void initSdk(Application app) {
        L.e(TAG, "Init SDK and set the connection listener");
        InitConfig initConfig = new InitConfig(Constants.getImApiUrl(),
                Constants.getImWsUrl(), getStorageDir());
        initConfig.isLogStandardOutput = true;
        initConfig.logLevel=5;
        initConfig.logFilePath = Constants.getFileDir();

        ///IM 初始化 設置網絡鏈接狀態監聽
        OpenIMClient.getInstance().initSDK(app,
                initConfig, IMEvent.getInstance().connListener);

        IMEvent.getInstance().init();
    }

    //存储路径
    public static String getStorageDir() {
        return BaseApp.inst().getFilesDir().getAbsolutePath();
    }
}
