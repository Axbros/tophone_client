package com.openim.tophone.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class AppBootstrapClient {
    private static final String TAG = "AppBootstrap";
    private static final String APP_NAME = "device_android";
    private static final String PREFS = "app_bootstrap";
    private static final String KEY_API_BASE = "api_base_url";
    private static final String KEY_RTC_BASE = "rtc_base_url";
    private static final String KEY_MQTT_TCP = "mqtt_tcp";
    private static final String KEY_CONFIG_VERSION = "config_version";
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onComplete(boolean updated);
    }

    private AppBootstrapClient() {
    }

    public static void applyCached(Context context) {
        SharedPreferences sp = prefs(context);
        apply(
                sp.getString(KEY_API_BASE, ""),
                sp.getString(KEY_RTC_BASE, ""),
                sp.getString(KEY_MQTT_TCP, "")
        );
    }

    public static void refresh(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean updated = false;
            String bootstrapUrl = Constants.getBootstrapUrl();
            try {
                BootstrapPayload payload = fetchFrom(bootstrapUrl);
                save(app, payload);
                apply(payload.apiBaseUrl, payload.rtcBaseUrl, payload.mqttTcp);
                updated = true;
                Log.i(TAG, "bootstrap ok url=" + bootstrapUrl + " api=" + payload.apiBaseUrl);
            } catch (Exception e) {
                Log.w(TAG, "bootstrap failed url=" + bootstrapUrl + " err=" + e.getMessage());
            }
            boolean result = updated;
            MAIN.post(() -> {
                if (callback != null) {
                    callback.onComplete(result);
                }
            });
        });
    }

    private static BootstrapPayload fetchFrom(String bootstrapUrl) throws Exception {
        String url = bootstrapUrl + "?app=" + urlEncode(APP_NAME);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            JSONObject root = new JSONObject(response.body().string());
            if (root.optInt("code", -1) != 0) {
                throw new IOException(root.optString("msg", "bootstrap rejected"));
            }
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                throw new IOException("payload missing");
            }
            String apiBase = data.optString("apiBaseUrl", "").trim();
            if (TextUtils.isEmpty(apiBase)) {
                throw new IOException("apiBaseUrl missing");
            }
            BootstrapPayload payload = new BootstrapPayload();
            payload.apiBaseUrl = apiBase;
            payload.rtcBaseUrl = data.optString("rtcBaseUrl", apiBase).trim();
            payload.mqttTcp = firstNonEmpty(data.optString("mqttTcp", ""), data.optString("mqttWss", ""));
            payload.configVersion = data.optInt("configVersion", 0);
            return payload;
        }
    }

    private static void save(Context context, BootstrapPayload payload) {
        prefs(context).edit()
                .putString(KEY_API_BASE, payload.apiBaseUrl)
                .putString(KEY_RTC_BASE, payload.rtcBaseUrl)
                .putString(KEY_MQTT_TCP, payload.mqttTcp)
                .putInt(KEY_CONFIG_VERSION, payload.configVersion)
                .apply();
    }

    private static void apply(String apiBaseUrl, String rtcBaseUrl, String mqttTcp) {
        if (!TextUtils.isEmpty(apiBaseUrl)) {
            Constants.updateApiBaseUrl(apiBaseUrl);
        }
        if (!TextUtils.isEmpty(rtcBaseUrl)) {
            Constants.updateRtcBaseUrl(rtcBaseUrl);
        }
        if (!TextUtils.isEmpty(mqttTcp)) {
            Constants.updateMqttBrokerTcp(mqttTcp);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String firstNonEmpty(String first, String second) {
        if (!TextUtils.isEmpty(first != null ? first.trim() : "")) {
            return first.trim();
        }
        return second != null ? second.trim() : "";
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }

    private static final class BootstrapPayload {
        String apiBaseUrl;
        String rtcBaseUrl;
        String mqttTcp;
        int configVersion;
    }
}
