package com.openim.tophone.mqtt;

import android.content.Context;
import android.text.TextUtils;

import com.openim.tophone.openim.entity.CheckVersionDataResp;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.L;

/**
 * MQTT 生命周期：check_version 成功后连接，供 MainApplication 调用。
 */
public class MqttManager {

    private static final String TAG = "MqttManager";
    private static MqttManager instance;

    private MqttCommandClient client;

    public static synchronized MqttManager getInstance() {
        if (instance == null) {
            instance = new MqttManager();
        }
        return instance;
    }

    public void connectAfterCheckIn(Context context, String deviceId, CheckVersionDataResp data) {
        if (!Constants.isUseMqtt()) {
            return;
        }
        if (data == null || !data.hasMqttCredentials()) {
            L.w(TAG, "no mqtt credentials in check_version response, skip MQTT");
            return;
        }
        if (TextUtils.isEmpty(deviceId)) {
            L.w(TAG, "deviceId empty, skip MQTT");
            return;
        }

        String broker = resolveMqttBroker(data.resolveMqttBroker());
        String username = data.resolveMqttUsername(deviceId);
        String token = data.resolveMqttToken();

        L.d(TAG, "connect MQTT broker=" + broker + " user=" + username);
        if (client != null) {
            client.disconnect();
        }
        client = new MqttCommandClient(context, deviceId);
        client.connect(broker, username, token);
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public void publishEvent(String type, String mobile, String content) {
        if (client != null) {
            client.publishEvent(type, mobile, content);
        }
    }

    public void disconnect() {
        if (client != null) {
            client.disconnect();
            client = null;
        }
    }

    /**
     * 后端 brokerTCP 多为 127.0.0.1（给服务端自用）；设备/模拟器须用 Constants 里的局域网或 10.0.2.2。
     */
    private static String resolveMqttBroker(String serverBroker) {
        if (Constants.USE_LOCAL_LAN) {
            return Constants.getMqttBrokerTcp();
        }
        if (!TextUtils.isEmpty(serverBroker)) {
            return serverBroker;
        }
        return Constants.getMqttBrokerTcp();
    }
}
