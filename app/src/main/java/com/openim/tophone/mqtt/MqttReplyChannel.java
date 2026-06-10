package com.openim.tophone.mqtt;

/**
 * 将 ToPhone 执行结果 publish 到 tophone/ack/{deviceId}。
 */
public class MqttReplyChannel implements CommandReplyChannel {

    public interface Publisher {
        void publish(String topic, String payload, int qos) throws Exception;
    }

    private final String deviceId;
    private final Publisher publisher;

    public MqttReplyChannel(String deviceId, Publisher publisher) {
        this.deviceId = deviceId;
        this.publisher = publisher;
    }

    @Override
    public void sendAck(String requestId, boolean ok, String type, String message) {
        try {
            org.json.JSONObject ack = new org.json.JSONObject();
            if (requestId != null && !requestId.isEmpty()) {
                ack.put("requestId", requestId);
                ack.put("ok", ok);
                ack.put("deviceId", deviceId);
                ack.put("type", type != null ? type : "");
                ack.put("message", message != null ? message : "");
                ack.put("ts", System.currentTimeMillis());
            } else {
                ack.put("ok", ok);
                ack.put("deviceId", deviceId);
                ack.put("message", message != null ? message : "");
                ack.put("ts", System.currentTimeMillis());
            }
            publisher.publish("tophone/ack/" + deviceId, ack.toString(), 0);
        } catch (Exception e) {
            com.openim.tophone.utils.L.e("MqttReplyChannel", "sendAck failed: " + e.getMessage());
        }
    }
}
