package com.openim.tophone.mqtt;

/**
 * 指令回复通道 — ToPhone 与 MQTT 解耦。
 */
public interface CommandReplyChannel {

    /**
     * @param type 对应下行指令 type，ACK 必填（新协议）
     */
    void sendAck(String requestId, boolean ok, String type, String message);

    default void sendReply(String content, String toControllerId) {
        sendAck(null, true, "", content);
    }
}
