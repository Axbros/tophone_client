package com.openim.tophone.utils;

import com.openim.tophone.enums.ActionEnums;
import com.openim.tophone.mqtt.MqttManager;
import com.openim.tophone.openim.entity.CallLogBean;

import java.util.UUID;

/**
 * 设备上行事件（来电、挂机、短信）通过 MQTT 发布。
 */
public final class MqttEventUtil {

    private MqttEventUtil() {
    }

    public static void publishEvent(String type, String mobile, String content) {
        MqttManager.getInstance().publishEvent(type, mobile, content);
    }

    /** 系统通话记录落库后，补发包含系统 DURATION 的最终通话结果。 */
    public static void publishCallRecord(CallLogBean callLog) {
        MqttManager.getInstance().publishCallRecord(callLog);
    }

    /** 短信上行：tophone/sms/{deviceId} QoS 1，并保留 event 通道兼容 */
    public static void publishSmsReceived(String mobile, String content, long deviceTime) {
        String messageId = UUID.randomUUID().toString();
        MqttManager.getInstance().publishSmsUplink(messageId, mobile, content, deviceTime);
        MqttManager.getInstance().publishEvent(ActionEnums.RECEIVED_SMS.getType(), mobile, content);
    }
}
