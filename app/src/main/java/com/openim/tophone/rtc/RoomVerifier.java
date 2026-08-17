package com.openim.tophone.rtc;

import android.content.Context;

import com.openim.tophone.utils.Constants;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RoomVerifier {

    private static final OkHttpClient client = new OkHttpClient();

    public interface RoomCallback {
        void onResult(boolean isExist, String token, String appID);

        void onError(Exception e);

        void onMessage(String message);
    }

    public static void verifyRoom(String roomID, String userID, Context context, RoomCallback callback) {
        JSONObject json = new JSONObject();
        try {
            json.put("roomID", roomID);
            json.put("userID", userID);
            json.put("deviceType", "android_device");
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json")
        );
        Request request = new Request.Builder()
                .url(Constants.getVerifyRoomURL())
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        callback.onError(new IOException("Unexpected code " + response));
                        return;
                    }

                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    int responseBodyCode = jsonResponse.getInt("code");
                    String message = jsonResponse.getString("msg");
                    if (responseBodyCode != 0) {
                        callback.onMessage(message);
                        return;
                    }
                    JSONObject data = jsonResponse.getJSONObject("data");
                    boolean isExist = data.getBoolean("isExist");
                    String t = data.getString("token");
                    String appID = data.optString("appID", "").trim();
                    callback.onResult(isExist, t, appID);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }
}
