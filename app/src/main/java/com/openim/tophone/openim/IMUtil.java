package com.openim.tophone.openim;


import static com.openim.tophone.ui.main.MainActivity.sp;

import android.text.TextUtils;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.L;
import com.openim.tophone.utils.SharedPreferencesUtil;

import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.LoginStatus;
import io.openim.android.sdk.listener.OnBase;
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

        String recvUid = sp.getString(Constants.getGroupOwnerKey(),null);
        if (TextUtils.isEmpty(recvUid)){
            L.w("the group owner is null");
            return ;
        }
//        unexpected end of JSON input
        OfflinePushInfo offlinePushInfo = new OfflinePushInfo();
        //2、compose the json string
        String jsonString = "{\"type\":\"" + type + "\",\"mobile\":\"" + mobile + "\",\"content\":\"" + content + "\"}";

        L.d(TAG,"send message to parent:"+jsonString);

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
                L.d(TAG,"Message send successful! "+s.getTextElem().getContent());
            }
        },  message,  recvUid,  null,  offlinePushInfo);
    }

    public static class IMCallBack<T> implements OnBase<T> {
        @Override
        public void onError(int code, String error) {
            L.e("IMCallBack", "onError:(" + code + ")" + error);
        }

        public void onSuccess(T data) {

        }
    }
}
