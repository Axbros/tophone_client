package com.openim.tophone.utils;

import static com.openim.tophone.ui.main.MainActivity.sp;

import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import com.openim.tophone.R;
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
        String commandType = "";
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

            commandType = jsonObject.getString("type");
            String mobile = jsonObject.optString("mobile");
            String content = jsonObject.optString("content");

            handleCommandByType(commandType, mobile, content);

            success = true;
            resultMessage = buildSuccessMessage(commandType, mobile);
        } catch (Exception e) {
            L.e(TAG, "处理消息失败: " + e.getMessage());
            resultMessage = e.getMessage() != null ? e.getMessage() : context.getString(R.string.cmd_process_failed);
            replyFailure(requestId, commandType, resultMessage, fromUserID, jsonStr);
        }

        if (success) {
            replySuccess(requestId, commandType, resultMessage, fromUserID, jsonStr);
        }
    }

    private String buildSuccessMessage(String type, String mobile) {
        switch (type) {
            case "call":
                return context.getString(R.string.cmd_call_started);
            case "send_message":
                return context.getString(R.string.cmd_sms_sent);
            case "idle":
                return context.getString(R.string.cmd_hangup);
            case "answer":
                return context.getString(R.string.cmd_answered);
            case "block_phone":
                return context.getString(R.string.cmd_blocked);
            case "unblock_phone":
                return context.getString(R.string.cmd_unblocked);
            default:
                return context.getString(R.string.cmd_executed);
        }
    }

    private void replySuccess(String requestId, String type, String message, String fromUserID, String jsonStr) {
        if (requestId != null) {
            replyChannel.sendAck(requestId, true, type, message);
        } else {
            replyChannel.sendReply(context.getString(R.string.cmd_reply_success, jsonStr), fromUserID);
        }
    }

    private void replyFailure(String requestId, String type, String message, String fromUserID, String jsonStr) {
        if (requestId != null) {
            replyChannel.sendAck(requestId, false, type, message);
        } else {
            replyChannel.sendReply(context.getString(R.string.cmd_reply_error, message), fromUserID);
        }
    }

    private boolean handleSpecialCommands(String command, String fromUserID) {
        switch (command) {
            case "version":
                int version = AppUtils.getLocalVersionCode();
                replyChannel.sendReply(context.getString(R.string.cmd_device_version, version), fromUserID);
                return true;
            case "parent":
                String recvUid = sp.getString(Constants.getGroupOwnerKey(), null);
                String owner = recvUid != null ? recvUid : context.getString(R.string.cmd_owner_not_set);
                replyChannel.sendReply(context.getString(R.string.cmd_owner_id, owner), fromUserID);
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
                throw new IllegalArgumentException(context.getString(R.string.cmd_unknown_type, type));
        }
    }

    private void validateMobile(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) {
            throw new IllegalArgumentException(context.getString(R.string.cmd_missing_phone));
        }
    }

    private void validateMobileAndContent(String mobile, String content) {
        validateMobile(mobile);
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException(context.getString(R.string.cmd_missing_sms_body));
        }
    }

    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
