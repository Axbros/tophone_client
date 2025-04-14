package com.openim.tophone.openim;

import android.content.Context;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.L;
import com.openim.tophone.utils.SharedPreferencesUtil;

import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.LoginStatus;
import io.openim.android.sdk.listener.OnMsgSendCallback;
import io.openim.android.sdk.models.Message;
import io.openim.android.sdk.models.OfflinePushInfo;

public class IMUtil {

    private static final String TAG = "IMUtil";
    /**
     * 已登录或登录中
     *
     * @return
     */
    public static boolean isLogged() {
        long status = OpenIMClient.getInstance().getLoginStatus();
        return status == LoginStatus.Logging || status == LoginStatus.Logged;
    }

    public static void uploadMsg2Parent(String type,String mobile,String content){
        //1、get parent user id
        SharedPreferencesUtil sharedPreferencesUtil = new SharedPreferencesUtil(BaseApp.inst());
        String recvUid = sharedPreferencesUtil.getString(Constants.getGroupOwnerKey());
//        unexpected end of JSON input
        OfflinePushInfo offlinePushInfo = new OfflinePushInfo();
        //2、compose the json string
        String jsonString = "{\"type\":\"" + type + "\",\"mobile\":\"" + mobile + "\",\"content\":\"" + content + "\"}";


        Message message = OpenIMClient.getInstance().messageManager.createTextMessage(jsonString);

        //3、send the message to owner


        OpenIMClient.getInstance().messageManager.sendMessage(new OnMsgSendCallback(){


            @Override
            public void onError(int code, String error) {
                //发送失败
                L.d(TAG,"Message send failed! Message : " + error);
            }

            @Override
            public void onProgress(long progress) {
                //发送进度
            }

            @Override
            public void onSuccess(Message s) {
                //发送成功
                L.d(TAG,"Message send successful! ");
            }
        },  message,  recvUid,  null,  offlinePushInfo);
    }
}
