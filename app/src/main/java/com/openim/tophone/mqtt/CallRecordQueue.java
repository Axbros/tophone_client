package com.openim.tophone.mqtt;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** MQTT 断线时持久化最终通话记录，重连后按 callId 补发。 */
final class CallRecordQueue {
    private static final String PREFS = "call_record_queue";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 100;

    private CallRecordQueue() {
    }

    static void enqueue(Context context, String callId, String payload) {
        if (context == null || callId == null || callId.isEmpty() || payload == null) {
            return;
        }
        SharedPreferences sp = prefs(context);
        JSONArray current = readArray(sp);
        JSONArray next = new JSONArray();
        next.put(toJson(callId, payload));
        for (int i = 0; i < current.length() && next.length() < MAX_ITEMS; i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null || callId.equals(item.optString("callId"))) {
                continue;
            }
            next.put(item);
        }
        sp.edit().putString(KEY_ITEMS, next.toString()).apply();
    }

    static List<Item> peekAll(Context context) {
        JSONArray current = readArray(prefs(context));
        List<Item> items = new ArrayList<>(current.length());
        for (int i = 0; i < current.length(); i++) {
            Item item = Item.fromJson(current.optJSONObject(i));
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    static void remove(Context context, String callId) {
        if (context == null || callId == null || callId.isEmpty()) {
            return;
        }
        SharedPreferences sp = prefs(context);
        JSONArray current = readArray(sp);
        JSONArray next = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null || callId.equals(item.optString("callId"))) {
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
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static JSONObject toJson(String callId, String payload) {
        JSONObject item = new JSONObject();
        try {
            item.put("callId", callId);
            item.put("payload", payload);
        } catch (Exception ignored) {
        }
        return item;
    }

    static final class Item {
        final String callId;
        final String payload;

        private Item(String callId, String payload) {
            this.callId = callId;
            this.payload = payload;
        }

        static Item fromJson(JSONObject json) {
            if (json == null) {
                return null;
            }
            String callId = json.optString("callId", "");
            String payload = json.optString("payload", "");
            if (callId.isEmpty() || payload.isEmpty()) {
                return null;
            }
            return new Item(callId, payload);
        }
    }
}
