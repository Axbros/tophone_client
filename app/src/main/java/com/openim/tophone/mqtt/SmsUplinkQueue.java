package com.openim.tophone.mqtt;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * MQTT 离线时缓存短信上行，重连后补发（同一 messageId 幂等）。
 */
public final class SmsUplinkQueue {
    private static final String PREFS = "sms_uplink_queue";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 100;

    private SmsUplinkQueue() {
    }

    public static void enqueue(Context context, String messageId, String mobile, String content, long deviceTime) {
        if (context == null || messageId == null || messageId.isEmpty()) {
            return;
        }
        SharedPreferences sp = prefs(context);
        JSONArray arr = readArray(sp);
        JSONArray next = new JSONArray();
        next.put(toJson(messageId, mobile, content, deviceTime));
        for (int i = 0; i < arr.length() && next.length() < MAX_ITEMS; i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (messageId.equals(item.optString("messageId"))) {
                continue;
            }
            next.put(item);
        }
        sp.edit().putString(KEY_ITEMS, next.toString()).apply();
    }

    public static List<Item> peekAll(Context context) {
        JSONArray arr = readArray(prefs(context));
        List<Item> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            Item item = Item.fromJson(obj);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    public static void remove(Context context, String messageId) {
        if (context == null || messageId == null || messageId.isEmpty()) {
            return;
        }
        SharedPreferences sp = prefs(context);
        JSONArray arr = readArray(sp);
        JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null || messageId.equals(item.optString("messageId"))) {
                continue;
            }
            next.put(item);
        }
        sp.edit().putString(KEY_ITEMS, next.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray readArray(SharedPreferences sp) {
        try {
            return new JSONArray(sp.getString(KEY_ITEMS, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static JSONObject toJson(String messageId, String mobile, String content, long deviceTime) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("messageId", messageId);
            obj.put("mobile", mobile != null ? mobile : "");
            obj.put("content", content != null ? content : "");
            obj.put("deviceTime", deviceTime);
        } catch (Exception ignored) {
        }
        return obj;
    }

    public static final class Item {
        public final String messageId;
        public final String mobile;
        public final String content;
        public final long deviceTime;

        private Item(String messageId, String mobile, String content, long deviceTime) {
            this.messageId = messageId;
            this.mobile = mobile;
            this.content = content;
            this.deviceTime = deviceTime;
        }

        static Item fromJson(JSONObject obj) {
            String messageId = obj.optString("messageId", "");
            if (messageId.isEmpty()) {
                return null;
            }
            return new Item(
                    messageId,
                    obj.optString("mobile", ""),
                    obj.optString("content", ""),
                    obj.optLong("deviceTime", System.currentTimeMillis())
            );
        }
    }
}
