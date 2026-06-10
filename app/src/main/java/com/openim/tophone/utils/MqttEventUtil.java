package com.openim.tophone.utils;

import com.openim.tophone.mqtt.MqttManager;

/**
 * 设备上行事件（来电、挂机、短信）通过 MQTT 发布，替代 OpenIM 单聊。
 */
public final class MqttEventUtil {

    private MqttEventUtil() {
    }

    public static void publishEvent(String type, String mobile, String content) {
        MqttManager.getInstance().publishEvent(type, mobile, content);
    }
}
