package com.openim.tophone.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import org.json.JSONObject;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.listener.OnMsgSendCallback;
import io.openim.android.sdk.models.Message;
import io.openim.android.sdk.models.OfflinePushInfo;

public class ToPhone {
    private String TAG = "ToPhone Utils";
    private PhoneUtils phoneUtils = new PhoneUtils();
    private OfflinePushInfo offlinePushInfo = new OfflinePushInfo();


    private OnMsgSendCallback onMsgSendCallback = new OnMsgSendCallback() {
        @Override
        public void onError(int code, String error) {
            L.e(TAG, "消息发送失败: " + code + ", " + error);
        }

        @Override
        public void onProgress(long progress) {
            // 可以添加进度显示逻辑
        }

        @Override
        public void onSuccess(Message message) {
            L.d(TAG, "消息发送成功: " + message.getTextElem().getContent());
        }
    };

    public void handleMessage(String jsonStr, String fromUserID) {
        boolean success = false;

        try {
            // 将JSON字符串解析为JSONObject对象
            JSONObject jsonObject = new JSONObject(jsonStr);
            L.d(TAG, "message in json: " + jsonObject);

            // 获取各个键对应的值
            String type = jsonObject.getString("type");
            String mobile = jsonObject.optString("mobile");
            String content = jsonObject.optString("content");

            switch (type) {
                case "idle":
                    phoneUtils.hangUpCall();
                    break;
                case "answer":
                    phoneUtils.answerCall();
                    break;
                case "call":
                    if (mobile.isEmpty()) {
                        throw new IllegalArgumentException("缺少电话号码");
                    }
                    phoneUtils.makePhoneCall(mobile);
                    break;
                case "send_message":
                    if (mobile.isEmpty() || content.isEmpty()) {
                        throw new IllegalArgumentException("缺少电话号码或内容");
                    }
                    phoneUtils.sendSms(mobile, content);
                    break;
                case "version":
                    int version = AppUtils.getLocalVersionCode();
                    Message message = OpenIMClient.getInstance().messageManager.createTextMessage("当前设备版本号：" + version);
                    OpenIMClient.getInstance().messageManager.sendMessage(onMsgSendCallback, message, fromUserID, null, offlinePushInfo);
                    break;
                default:
                    throw new IllegalArgumentException("未知指令类型: " + type);
            }

            success = true;
        } catch (Exception e) {
            L.e(TAG, "处理消息失败: " + e.getMessage());
            sendErrorMessage("处理指令失败: " + e.getMessage(), fromUserID);
        } finally {
            // 只在成功处理时发送确认消息
            if (success) {
                try {
                    Message message = OpenIMClient.getInstance().messageManager.createTextMessage("已成功处理您的指令");
                    OpenIMClient.getInstance().messageManager.sendMessage(onMsgSendCallback, message, fromUserID, null, offlinePushInfo);
                } catch (Exception e) {
                    L.e(TAG, "发送确认消息失败: " + e.getMessage());
                }
            }
        }
    }

    private void sendErrorMessage(String error, String fromUserID) {
        try {
            Message message = OpenIMClient.getInstance().messageManager.createTextMessage("错误: " + error);
            OpenIMClient.getInstance().messageManager.sendMessage(onMsgSendCallback, message, fromUserID, null, offlinePushInfo);
        } catch (Exception e) {
            L.e(TAG, "发送错误消息失败: " + e.getMessage());
        }
    }

}