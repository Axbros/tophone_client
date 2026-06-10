package com.openim.tophone.mqtt;

import android.content.Context;

import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.openim.entity.DevicePresenceReq;
import com.openim.tophone.stroage.VMStore;
import com.openim.tophone.repository.MqttApi;
import com.openim.tophone.utils.L;
import com.openim.tophone.utils.ToPhone;

import io.reactivex.schedulers.Schedulers;

import info.mqtt.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

/**
 * 设备端 MQTT：订阅 cmd (QoS1)，发布 ack/status + LWT (QoS0)。
 */
public class MqttCommandClient implements MqttCallback {

    private static final String TAG = "MqttCommandClient";
    private static final int QOS_CMD = 1;
    private static final int QOS_ACK = 0;

    private final Context appContext;
    private final String deviceId;
    private final ToPhone toPhone;
    private final RequestIdDedup dedup = new RequestIdDedup();
    private MqttAndroidClient client;
    private MqttReplyChannel replyChannel;

    public MqttCommandClient(Context context, String deviceId) {
        this.appContext = context.getApplicationContext();
        this.deviceId = deviceId;
        this.replyChannel = new MqttReplyChannel(deviceId, this::publishInternal);
        this.toPhone = new ToPhone(replyChannel);
    }

    public void connect(String brokerUri, String username, String mqttToken) {
        disconnectQuietly();

        String clientId = "device_" + deviceId;
        client = new MqttAndroidClient(appContext, brokerUri, clientId);
        client.setCallback(this);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setUserName(username);
        options.setPassword(mqttToken.toCharArray());
        options.setConnectionTimeout(15);
        options.setKeepAliveInterval(30);

        long ts = System.currentTimeMillis();
        String willTopic = "tophone/status/" + deviceId;
        String willPayload = "{\"online\":false,\"deviceId\":\"" + deviceId + "\",\"ts\":" + ts + "}";
        options.setWill(willTopic, willPayload.getBytes(), QOS_ACK, false);

        VMStore.get().isLoading.setValue(true);
        VMStore.get().connectionStatus.setValue(false);

        try {
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    L.d(TAG, "MQTT connected, deviceId=" + deviceId);
                    subscribeCmd();
                    publishStatus(true);
                    VMStore.get().isLoading.setValue(false);
                    VMStore.get().connectionStatus.setValue(true);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    L.e(TAG, "MQTT connect failed: " + exception.getMessage()
                            + " broker=" + brokerUri + " user=" + username);
                    VMStore.get().isLoading.setValue(false);
                    VMStore.get().connectionStatus.setValue(false);
                }
            });
        } catch (Exception e) {
            L.e(TAG, "connect exception: " + e.getMessage());
            VMStore.get().isLoading.setValue(false);
            VMStore.get().connectionStatus.setValue(false);
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    private void subscribeCmd() {
        try {
            client.subscribe("tophone/cmd/" + deviceId, QOS_CMD);
        } catch (Exception e) {
            L.e(TAG, "subscribe failed: " + e.getMessage());
        }
    }

    private void publishStatus(boolean online) {
        try {
            JSONObject status = new JSONObject();
            status.put("online", online);
            status.put("deviceId", deviceId);
            status.put("ts", System.currentTimeMillis());
            publishInternal("tophone/status/" + deviceId, status.toString(), QOS_ACK);
            reportPresenceToServer(online);
        } catch (Exception e) {
            L.e(TAG, "publishStatus failed: " + e.getMessage());
        }
    }

    private void reportPresenceToServer(boolean online) {
        if (!N.isInitialized()) {
            return;
        }
        try {
            N.mAPI(MqttApi.class)
                    .reportPresence(new DevicePresenceReq(deviceId, online))
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            resp -> L.d(TAG, "presence reported online=" + online),
                            err -> L.w(TAG, "presence report failed: " + err.getMessage())
                    );
        } catch (Exception e) {
            L.w(TAG, "presence report skipped: " + e.getMessage());
        }
    }

    private void publishInternal(String topic, String payload, int qos) throws Exception {
        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("MQTT not connected");
        }
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(qos);
        message.setRetained(false);
        client.publish(topic, message);
    }

    public void publishEvent(String type, String mobile, String content) {
        if (client == null || !client.isConnected()) {
            L.w(TAG, "MQTT not connected, skip event: " + type);
            return;
        }
        try {
            JSONObject event = new JSONObject();
            event.put("type", type);
            event.put("mobile", mobile != null ? mobile : "");
            event.put("content", content != null ? content : "");
            event.put("deviceId", deviceId);
            event.put("ts", System.currentTimeMillis());
            publishInternal("tophone/event/" + deviceId, event.toString(), QOS_ACK);
        } catch (Exception e) {
            L.e(TAG, "publishEvent failed: " + e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        L.w(TAG, "connection lost: " + (cause != null ? cause.getMessage() : ""));
        VMStore.get().connectionStatus.setValue(false);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String body = new String(message.getPayload());
        L.d(TAG, "cmd: " + body);
        try {
            JSONObject json = new JSONObject(body);
            String requestId = json.optString("requestId", null);
            String type = json.optString("type", "");
            if (requestId != null && !requestId.isEmpty() && dedup.isDuplicate(requestId)) {
                L.w(TAG, "duplicate requestId, skip: " + requestId);
                replyChannel.sendAck(requestId, true, type, "duplicate ignored");
                return;
            }
        } catch (Exception ignored) {
            // 非 JSON 指令（version/parent）继续走 ToPhone
        }
        toPhone.handleMessage(body, "controller");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    public void disconnect() {
        if (client != null && client.isConnected()) {
            try {
                publishStatus(false);
            } catch (Exception ignored) {
            }
            disconnectQuietly();
        }
        VMStore.get().connectionStatus.setValue(false);
    }

    private void disconnectQuietly() {
        if (client == null) return;
        try {
            client.disconnect();
        } catch (Exception ignored) {
        }
        client = null;
    }
}
