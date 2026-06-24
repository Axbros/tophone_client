package com.openim.tophone.mqtt;

import android.content.Context;

import com.openim.tophone.MainApplication;
import com.openim.tophone.rtc.RtcBackgroundJoiner;
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
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

import java.util.List;

/**
 * 设备端 MQTT：订阅 cmd (QoS1)，发布 ack/status + LWT (QoS0)。
 */
public class MqttCommandClient implements MqttCallbackExtended {

    private static final String TAG = "MqttCommandClient";
    private static final int QOS_CMD = 1;
    private static final int QOS_SMS = 1;
    private static final int QOS_ACK = 0;

    private final Context appContext;
    private final String deviceId;
    private final ToPhone toPhone;
    private final RequestIdDedup dedup = new RequestIdDedup();
    private final SmsDedup smsDedup = new SmsDedup();
    private MqttAndroidClient client;
    private MqttReplyChannel replyChannel;
    private volatile boolean connecting;

    public MqttCommandClient(Context context, String deviceId) {
        this.appContext = context.getApplicationContext();
        this.deviceId = deviceId;
        this.replyChannel = new MqttReplyChannel(deviceId, this::publishInternal);
        this.toPhone = new ToPhone(replyChannel);
    }

    public void connect(String brokerUri, String username, String mqttToken) {
        connect(brokerUri, username, mqttToken, null);
    }

    public void connect(String brokerUri, String username, String mqttToken, Runnable onAuthFailure) {
        if (connecting) {
            L.d(TAG, "connect skipped: already in flight");
            return;
        }
        if (client != null && client.isConnected()) {
            L.d(TAG, "connect skipped: already connected");
            return;
        }
        disconnectQuietly();

        String user = username != null ? username.trim() : "";
        String pwd = mqttToken != null ? mqttToken.trim() : "";
        if (user.isEmpty() || pwd.isEmpty()) {
            L.e(TAG, "MQTT connect skipped: empty username or password");
            if (onAuthFailure != null) {
                onAuthFailure.run();
            }
            return;
        }

        String clientId = "device_" + deviceId;
        client = new MqttAndroidClient(appContext, brokerUri, clientId);
        client.setCallback(this);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        options.setCleanSession(true);
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setUserName(user);
        options.setPassword(pwd.toCharArray());
        options.setConnectionTimeout(15);
        options.setKeepAliveInterval(30);

        long ts = System.currentTimeMillis();
        String willTopic = "tophone/status/" + deviceId;
        String willPayload = "{\"online\":false,\"deviceId\":\"" + deviceId + "\",\"ts\":" + ts + "}";
        options.setWill(willTopic, willPayload.getBytes(), QOS_ACK, false);

        setVmLoading(true);
        setVmConnectionStatus(false);
        connecting = true;

        try {
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    connecting = false;
                    L.d(TAG, "MQTT connected, deviceId=" + deviceId);
                    subscribeCmd();
                    subscribeMeta();
                    publishStatus(true);
                    flushSmsQueue();
                    setVmLoading(false);
                    setVmConnectionStatus(true);
                    RtcBackgroundJoiner.get().tryJoinWhenReady();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    connecting = false;
                    int reasonCode = exception instanceof MqttException
                            ? ((MqttException) exception).getReasonCode()
                            : -1;
                    L.e(TAG, "MQTT connect failed: " + exception.getMessage()
                            + " reasonCode=" + reasonCode
                            + " broker=" + brokerUri + " user=" + user + " jwtLen=" + pwd.length());
                    setVmLoading(false);
                    setVmConnectionStatus(false);
                    if (onAuthFailure != null && isAuthFailure(exception)) {
                        onAuthFailure.run();
                    }
                }
            });
        } catch (Exception e) {
            connecting = false;
            L.e(TAG, "connect exception: " + e.getMessage());
            setVmLoading(false);
            setVmConnectionStatus(false);
        }
    }

    private static boolean isAuthFailure(Throwable exception) {
        if (exception == null) {
            return false;
        }
        if (exception instanceof MqttException) {
            int rc = ((MqttException) exception).getReasonCode();
            if (rc == MqttException.REASON_CODE_NOT_AUTHORIZED) {
                return true;
            }
        }
        String msg = exception.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("not authorized")
                || lower.contains("not authorised")
                || lower.contains("bad user name or password")
                || lower.contains("invalid credentials");
    }

    public boolean isConnecting() {
        return connecting;
    }

    private static void setVmLoading(boolean loading) {
        if (!VMStore.isInitialized()) {
            return;
        }
        VMStore.get().isLoading.postValue(loading);
    }

    private static void setVmConnectionStatus(boolean connected) {
        if (!VMStore.isInitialized()) {
            return;
        }
        VMStore.get().connectionStatus.postValue(connected);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        L.d(TAG, "connectComplete reconnect=" + reconnect + " uri=" + serverURI);
        if (reconnect) {
            subscribeCmd();
            subscribeMeta();
            try {
                publishStatus(true);
            } catch (Exception e) {
                L.w(TAG, "publishStatus after reconnect failed: " + e.getMessage());
            }
            flushSmsQueue();
            setVmConnectionStatus(true);
            RtcBackgroundJoiner.get().tryJoinWhenReady();
        }
    }

    private void handleMetaMessage(String body) {
        try {
            JSONObject json = new JSONObject(body);
            String type = json.optString("type", "");
            if ("assigned".equals(type) || "unassigned".equals(type)) {
                L.i(TAG, "group meta type=" + type);
                MainApplication app = (MainApplication) appContext;
                app.triggerDeviceProfileRefresh();
                return;
            }
            if (!"policy".equals(type)) {
                return;
            }
            boolean voiceDisabled = json.optBoolean("voiceDisabled", false);
            boolean smsDisabled = json.optBoolean("smsDisabled", false);
            int status = json.optInt("status", 1);
            L.i(TAG, "policy meta voiceDisabled=" + voiceDisabled
                    + " smsDisabled=" + smsDisabled + " status=" + status);
            if (!VMStore.isInitialized()) {
                return;
            }
            VMStore.get().applyDevicePolicy(voiceDisabled, smsDisabled, status);
        } catch (Exception e) {
            L.e(TAG, "handleMetaMessage failed: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    private void subscribeCmd() {
        subscribeTopic("tophone/cmd/" + deviceId, QOS_CMD);
    }

    private void subscribeMeta() {
        subscribeTopic("tophone/meta/" + deviceId, QOS_ACK);
    }

    private void subscribeTopic(String topic, int qos) {
        if (client == null) {
            L.w(TAG, "subscribe skipped, client null: " + topic);
            return;
        }
        try {
            client.subscribe(topic, qos, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    L.i(TAG, "subscribed " + topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    L.e(TAG, "subscribe failed " + topic + ": "
                            + (exception != null ? exception.getMessage() : "unknown"));
                }
            });
        } catch (Exception e) {
            L.e(TAG, "subscribe exception " + topic + ": " + e.getMessage());
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

    /** 短信上行可靠存储：publish tophone/sms/{deviceId} QoS 1 */
    public void publishSmsUplink(String messageId, String mobile, String content, long deviceTime) {
        if (messageId == null || messageId.isEmpty()) {
            L.w(TAG, "publishSmsUplink skipped: empty messageId");
            return;
        }
        if (smsDedup.isDuplicate(mobile, content, deviceTime)) {
            L.d(TAG, "publishSmsUplink skipped duplicate");
            return;
        }
        if (client == null || !client.isConnected()) {
            L.w(TAG, "MQTT not connected, queue sms uplink");
            SmsUplinkQueue.enqueue(appContext, messageId, mobile, content, deviceTime);
            return;
        }
        try {
            publishSmsUplinkInternal(messageId, mobile, content, deviceTime);
            SmsUplinkQueue.remove(appContext, messageId);
        } catch (Exception e) {
            L.w(TAG, "publishSmsUplink failed, queue: " + e.getMessage());
            SmsUplinkQueue.enqueue(appContext, messageId, mobile, content, deviceTime);
        }
    }

    private void publishSmsUplinkInternal(String messageId, String mobile, String content, long deviceTime)
            throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("messageId", messageId);
        payload.put("mobile", mobile != null ? mobile : "");
        payload.put("content", content != null ? content : "");
        payload.put("deviceTime", deviceTime);
        payload.put("ts", System.currentTimeMillis());
        publishInternal("tophone/sms/" + deviceId, payload.toString(), QOS_SMS);
        L.d(TAG, "sms uplink published messageId=" + messageId);
    }

    private void flushSmsQueue() {
        while (true) {
            List<SmsUplinkQueue.Item> pending = SmsUplinkQueue.peekAll(appContext);
            if (pending.isEmpty()) {
                return;
            }
            SmsUplinkQueue.Item item = pending.get(0);
            try {
                publishSmsUplinkInternal(item.messageId, item.mobile, item.content, item.deviceTime);
                SmsUplinkQueue.remove(appContext, item.messageId);
            } catch (Exception e) {
                L.w(TAG, "flush sms uplink failed: " + e.getMessage());
                return;
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        L.w(TAG, "connection lost: " + (cause != null ? cause.getMessage() : ""));
        setVmConnectionStatus(false);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String body = new String(message.getPayload());
        if (topic != null && topic.startsWith("tophone/meta/")) {
            handleMetaMessage(body);
            return;
        }
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
        MqttAndroidClient c = client;
        client = null;
        connecting = false;
        if (c != null) {
            try {
                c.setCallback(null);
                if (c.isConnected()) {
                    try {
                        JSONObject status = new JSONObject();
                        status.put("online", false);
                        status.put("deviceId", deviceId);
                        status.put("ts", System.currentTimeMillis());
                        MqttMessage message = new MqttMessage(status.toString().getBytes());
                        message.setQos(QOS_ACK);
                        message.setRetained(false);
                        c.publish("tophone/status/" + deviceId, message);
                    } catch (Exception ignored) {
                    }
                    c.disconnect();
                }
                c.close();
            } catch (Exception ignored) {
            }
        }
        setVmConnectionStatus(false);
    }

    private void disconnectQuietly() {
        disconnect();
    }
}
