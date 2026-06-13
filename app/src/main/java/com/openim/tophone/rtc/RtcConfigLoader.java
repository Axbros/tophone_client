package com.openim.tophone.rtc;

import android.util.Log;

import com.openim.tophone.utils.Constants;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Loads RTC AppID from GET /api/v1/config/tophone_world. */
public final class RtcConfigLoader {

    private static final String TAG = "RtcConfigLoader";

    public interface AppIdCallback {
        void onSuccess(String appId);

        void onFailure(String message);
    }

    private RtcConfigLoader() {
    }

    public static void fetchAppId(OkHttpClient client, AppIdCallback callback) {
        Request request = new Request.Builder()
                .url(Constants.getRtcConfigURL())
                .addHeader("Accept", "application/json")
                .get()
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "fetch tophone_world failed: " + e.getMessage());
                callback.onFailure(e.getMessage() != null ? e.getMessage() : "network error");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response res = response) {
                    if (!res.isSuccessful() || res.body() == null) {
                        callback.onFailure("HTTP " + res.code());
                        return;
                    }
                    String appId = parseAppId(res.body().string());
                    if (appId == null || appId.isEmpty()) {
                        callback.onFailure("empty appID");
                        return;
                    }
                    Log.i(TAG, "tophone_world appID=" + appId);
                    callback.onSuccess(appId);
                } catch (Exception e) {
                    Log.e(TAG, "parse tophone_world failed: " + e.getMessage());
                    callback.onFailure(e.getMessage() != null ? e.getMessage() : "parse error");
                }
            }
        });
    }

    static String parseAppId(String responseBody) throws Exception {
        JSONObject jsonObject = new JSONObject(responseBody);
        int code = jsonObject.getInt("code");
        if (code != 0) {
            String msg = jsonObject.optString("msg", "unknown error");
            throw new IOException("code=" + code + " " + msg);
        }
        JSONObject data = jsonObject.getJSONObject("data");
        return data.getString("appID");
    }
}
