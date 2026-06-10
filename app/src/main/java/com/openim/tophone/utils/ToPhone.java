package com.openim.tophone.utils;

import static com.openim.tophone.ui.main.MainActivity.sp;

import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.mqtt.CommandReplyChannel;

import org.json.JSONObject;

public class ToPhone {
    private static final String TAG = "ToPhone Utils";

    private final PhoneUtils phoneUtils;
    private final CommandReplyChannel replyChannel;
    private final Context context;

    public ToPhone(CommandReplyChannel replyChannel) {
        this.context = BaseApp.inst();
        this.phoneUtils = new PhoneUtils();
        this.replyChannel = replyChannel;
    }

    public void handleMessage(String jsonStr, String fromUserID) {
        String requestId = null;
        boolean success = false;
        String resultMessage = null;

        try {
            if (handleSpecialCommands(jsonStr, fromUserID)) {
                return;
            }

            JSONObject jsonObject = new JSONObject(jsonStr);
            L.d(TAG, "message in json: " + jsonObject);

            requestId = jsonObject.optString("requestId", null);
            if (TextUtils.isEmpty(requestId)) {
                requestId = null;
            }

            String type = jsonObject.getString("type");
            String mobile = jsonObject.optString("mobile");
            String content = jsonObject.optString("content");

            handleCommandByType(type, mobile, content);

            success = true;
            resultMessage = buildSuccessMessage(type, mobile);
        } catch (Exception e) {
            L.e(TAG, "处理消息失败: " + e.getMessage());
            resultMessage = e.getMessage() != null ? e.getMessage() : "处理指令失败";
            replyFailure(requestId, resultMessage, fromUserID, jsonStr);
        }

        if (success) {
            replySuccess(requestId, resultMessage, fromUserID, jsonStr);
        }
    }

    private String buildSuccessMessage(String type, String mobile) {
        switch (type) {
            case "call":
                return "拨号已发起";
            case "send_message":
                return "短信已发送";
            case "idle":
                return "已挂机";
            case "answer":
                return "已接听";
            case "block_phone":
                return "已拉黑";
            case "unblock_phone":
                return "已取消拉黑";
            default:
                return "指令已执行";
        }
    }

    private void replySuccess(String requestId, String message, String fromUserID, String jsonStr) {
        if (requestId != null) {
            replyChannel.sendAck(requestId, true, message);
        } else {
            replyChannel.sendReply("已成功處理您的指令！ 指令：" + jsonStr, fromUserID);
        }
    }

    private void replyFailure(String requestId, String message, String fromUserID, String jsonStr) {
        if (requestId != null) {
            replyChannel.sendAck(requestId, false, message);
        } else {
            replyChannel.sendReply("错误: " + message, fromUserID);
        }
    }

    private boolean handleSpecialCommands(String command, String fromUserID) {
        switch (command) {
            case "version":
                int version = AppUtils.getLocalVersionCode();
                replyChannel.sendReply("当前设备版本号：" + version, fromUserID);
                return true;
            case "parent":
                String recvUid = sp.getString(Constants.getGroupOwnerKey(), null);
                String messageContent = "当前甲方ID：" + (recvUid != null ? recvUid : "未设置");
                replyChannel.sendReply(messageContent, fromUserID);
                return true;
            default:
                L.d(TAG, "Unknown command: " + command);
                return false;
        }
    }

    private void handleCommandByType(String type, String mobile, String content) {
        L.d(TAG, "new message：" + type + "|" + mobile + "|" + content);
        switch (type) {
            case "idle":
                phoneUtils.hangUpCall();
                break;
            case "answer":
                phoneUtils.answerCall();
                break;
            case "call":
                validateMobile(mobile);
                phoneUtils.makePhoneCall(mobile);
                break;
            case "send_message":
                validateMobileAndContent(mobile, content);
                phoneUtils.sendSms(mobile, content);
                break;
            case "block_phone":
                validateMobile(mobile);
                new CallBlocker(context).blockPhoneNumber(mobile);
                break;
            case "unblock_phone":
                validateMobile(mobile);
                new CallBlocker(context).unblockPhoneNumber(mobile);
                break;
            default:
                throw new IllegalArgumentException("未知指令类型: " + type);
        }
    }

    private void validateMobile(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少电话号码");
        }
    }

    private void validateMobileAndContent(String mobile, String content) {
        validateMobile(mobile);
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少短信内容");
        }
    }

    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
