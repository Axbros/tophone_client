package com.openim.tophone.utils;

import static com.openim.tophone.ui.main.MainActivity.sp;

import android.content.Context;
import android.widget.Toast;

import com.openim.tophone.base.BaseApp;

import org.json.JSONObject;

import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.listener.OnMsgSendCallback;
import io.openim.android.sdk.models.Message;
import io.openim.android.sdk.models.OfflinePushInfo;

public class ToPhone {
    // 使用static final定义TAG，符合最佳实践
    private static final String TAG = "ToPhone Utils";
    // 成员变量添加final修饰，确保不可变
    private final PhoneUtils phoneUtils;
    private final OfflinePushInfo offlinePushInfo;
    // 持有应用上下文，避免内存泄漏风险
    private final Context context;

    // 初始化成员变量的构造方法
    public ToPhone() {
        this.context = BaseApp.inst();
        this.phoneUtils = new PhoneUtils();
        this.offlinePushInfo = new OfflinePushInfo();
    }

    // 复用的消息发送回调
    private final OnMsgSendCallback onMsgSendCallback = new OnMsgSendCallback() {
        @Override
        public void onError(int code, String error) {
            L.e(TAG, "消息发送失败: " + code + ", " + error);
            showToast("消息发送失败: " + error);
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
            // 优先处理非JSON格式的特殊命令
            if (handleSpecialCommands(jsonStr, fromUserID)) {
                return;
            }

            // 解析JSON格式消息
            JSONObject jsonObject = new JSONObject(jsonStr);
            L.d(TAG, "message in json: " + jsonObject);

            String type = jsonObject.getString("type");
            String mobile = jsonObject.optString("mobile");
            String content = jsonObject.optString("content");

            // 根据类型处理不同指令
            handleCommandByType(type, mobile, content);

            success = true;
        } catch (Exception e) {
            L.e(TAG, "处理消息失败: " + e.getMessage());
            sendErrorMessage("处理指令失败: " + e.getMessage(), fromUserID);
        } finally {
            // 成功处理后发送确认消息
            if (success) {
                sendConfirmationMessage(jsonStr, fromUserID);
            }
        }
    }

    /**
     * 处理特殊命令（非JSON格式）
     */
    private boolean handleSpecialCommands(String command, String fromUserID) {
        switch (command) {
            case "version":
                int version = AppUtils.getLocalVersionCode();
                sendTextMessage("当前设备版本号：" + version, fromUserID);
                return true;
            case "parent":
                String recvUid = sp.getString(Constants.getGroupOwnerKey(), null);
                String messageContent = "当前甲方ID：" + (recvUid != null ? recvUid : "未设置");
                sendTextMessage(messageContent, fromUserID);
                return true;
            default:
                L.d(TAG, "Unknown command: " + command);
                return false;
        }
    }

    /**
     * 根据命令类型处理不同操作
     */
    private void handleCommandByType(String type, String mobile, String content) {
        L.d(TAG,"new message from openIM："+type+"|"+mobile+"|"+content);
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

    /**
     * 验证手机号不为空
     */
    private void validateMobile(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少电话号码");
        }
    }

    /**
     * 验证手机号和内容不为空
     */
    private void validateMobileAndContent(String mobile, String content) {
        validateMobile(mobile);
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少短信内容");
        }
    }

    /**
     * 发送确认消息
     */
    private void sendConfirmationMessage(String jsonStr, String fromUserID) {
        try {
            String messageContent = "已成功處理您的指令！ 指令：" + jsonStr;
            sendTextMessage(messageContent, fromUserID);
        } catch (Exception e) {
            L.e(TAG, "发送确认消息失败: " + e.getMessage());
        }
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(String error, String fromUserID) {
        sendTextMessage("错误: " + error, fromUserID);
    }

    /**
     * 发送文本消息（简化参数，复用offlinePushInfo）
     */
    private void sendTextMessage(String content, String fromUserId) {
        try {
            Message message = OpenIMClient.getInstance().messageManager.createTextMessage(content);
            OpenIMClient.getInstance().messageManager.sendMessage(
                    onMsgSendCallback,
                    message,
                    fromUserId,
                    null,
                    offlinePushInfo
            );
        } catch (Exception e) {
            L.e(TAG, "发送消息失败: " + e.getMessage());
        }
    }

    /**
     * 显示Toast消息
     */
    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
