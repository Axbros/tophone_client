package com.openim.tophone.mqtt;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.openim.entity.CheckVersionDataResp;
import com.openim.tophone.openim.entity.MqttDeviceTokenReq;
import com.openim.tophone.openim.entity.MqttTokenResp;
import com.openim.tophone.repository.MqttApi;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.L;

/**
 * MQTT 生命周期：check_version 成功后连接，供 MainApplication 调用。
 */
public class MqttManager {

    private static final String TAG = "MqttManager";
    private static final long CONNECT_COOLDOWN_MS = 15_000L;
    private static MqttManager instance;

    private MqttCommandClient client;
    private String activeDeviceId;
    private boolean tokenRefreshInFlight;
    private long lastConnectAttemptMs;

    public static synchronized MqttManager getInstance() {
        if (instance == null) {
            instance = new MqttManager();
        }
        return instance;
    }

    public synchronized void connectAfterCheckIn(Context context, String deviceId, CheckVersionDataResp data) {
        connectAfterCheckIn(context, deviceId, data, false);
    }

    public synchronized void connectAfterCheckIn(
            Context context,
            String deviceId,
            CheckVersionDataResp data,
            boolean force
    ) {
        if (!Constants.isUseMqtt()) {
            return;
        }
        if (TextUtils.isEmpty(deviceId)) {
            L.w(TAG, "deviceId empty, skip MQTT");
            return;
        }
        if (!force && shouldSkipConnect(deviceId)) {
            return;
        }
        if (data == null || !data.hasMqttCredentials()) {
            L.w(TAG, "no mqtt credentials in check_version response, try fetch token API");
            refreshTokenAndConnect(context, deviceId, resolveMqttBroker(null));
            return;
        }

        String broker = resolveMqttBroker(data.resolveMqttBroker());
        String username = data.resolveMqttUsername(deviceId);
        String token = data.resolveMqttToken();
        connectWithCredentials(context, deviceId, broker, username, token, true);
    }

    private boolean shouldSkipConnect(String deviceId) {
        if (client != null && client.isConnected()) {
            L.d(TAG, "already connected, skip MQTT connect");
            return true;
        }
        if (client != null && client.isConnecting()) {
            L.d(TAG, "connect in flight, skip MQTT connect");
            return true;
        }
        long elapsed = System.currentTimeMillis() - lastConnectAttemptMs;
        if (lastConnectAttemptMs > 0 && elapsed < CONNECT_COOLDOWN_MS) {
            L.d(TAG, "connect cooldown " + (CONNECT_COOLDOWN_MS - elapsed) + "ms, skip MQTT connect");
            return true;
        }
        return false;
    }

    private void connectWithCredentials(
            Context context,
            String deviceId,
            String broker,
            String username,
            String token,
            boolean allowTokenRefresh
    ) {
        if (!isValidJwt(token)) {
            L.e(TAG, "invalid mqtt jwt, len=" + (token != null ? token.length() : 0));
            if (allowTokenRefresh) {
                refreshTokenAndConnect(context, deviceId, broker);
            }
            return;
        }

        lastConnectAttemptMs = System.currentTimeMillis();
        L.d(TAG, "connect MQTT broker=" + broker + " user=" + username + " jwtLen=" + token.length());
        if (client != null && !deviceId.equals(activeDeviceId)) {
            client.disconnect();
            client = null;
        }
        if (client == null) {
            client = new MqttCommandClient(context, deviceId);
            activeDeviceId = deviceId;
        }
        client.connect(broker, username, token, () -> {
            if (allowTokenRefresh) {
                refreshTokenAndConnect(context, deviceId, broker);
            }
        });
    }

    @SuppressLint("CheckResult")
    private synchronized void refreshTokenAndConnect(Context context, String deviceId, String broker) {
        if (tokenRefreshInFlight) {
            return;
        }
        if (!N.isInitialized()) {
            L.w(TAG, "network not initialized, skip mqtt token refresh");
            return;
        }
        tokenRefreshInFlight = true;
        N.mAPI(MqttApi.class)
                .fetchDeviceToken(new MqttDeviceTokenReq(deviceId))
                .compose(N.IOMain())
                .subscribe(
                        resp -> {
                            tokenRefreshInFlight = false;
                            if (resp == null || resp.code != 0 || resp.data == null) {
                                L.e(TAG, "fetchDeviceToken failed: " + (resp != null ? resp.msg : "null"));
                                return;
                            }
                            String user = resp.data.username;
                            if (TextUtils.isEmpty(user)) {
                                user = "device_" + deviceId;
                            }
                            String token = resp.data.mqttToken;
                            String resolvedBroker = broker;
                            if (!TextUtils.isEmpty(resp.data.brokerTCP)) {
                                resolvedBroker = resolveMqttBroker(resp.data.brokerTCP);
                            }
                            connectWithCredentials(context, deviceId, resolvedBroker, user, token, false);
                        },
                        err -> {
                            tokenRefreshInFlight = false;
                            L.e(TAG, "fetchDeviceToken error: " + err.getMessage());
                        }
                );
    }

    private static boolean isValidJwt(String token) {
        if (TextUtils.isEmpty(token)) {
            return false;
        }
        String trimmed = token.trim();
        String[] parts = trimmed.split("\\.");
        return parts.length == 3 && parts[0].length() > 0 && parts[1].length() > 0 && parts[2].length() > 0;
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
        activeDeviceId = null;
        lastConnectAttemptMs = 0L;
    }

    /** 用户手动重连时绕过冷却，先断开再连。 */
    public synchronized void forceReconnect(Context context, String deviceId, CheckVersionDataResp data) {
        if (client != null) {
            client.disconnect();
        }
        lastConnectAttemptMs = 0L;
        connectAfterCheckIn(context, deviceId, data, true);
    }

    /**
     * 后端 brokerTCP 多为 127.0.0.1（给服务端自用）；设备端使用 Constants 自动解析的地址。
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
