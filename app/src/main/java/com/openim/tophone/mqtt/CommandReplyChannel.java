package com.openim.tophone.mqtt;

/**
 * 指令回复通道 — ToPhone 与 OpenIM / MQTT 解耦。
 */
public interface CommandReplyChannel {

    void sendAck(String requestId, boolean ok, String message);

    default void sendReply(String content, String toControllerId) {
        sendAck(null, true, content);
    }
}
