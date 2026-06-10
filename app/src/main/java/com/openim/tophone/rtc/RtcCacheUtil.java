package com.openim.tophone.rtc;

import android.content.Context;
import android.content.SharedPreferences;

public class RtcCacheUtil {
    private static final String PREF_NAME = "rtc_config";
    private static final String KEY_APP_ID = "app_id";

    private final SharedPreferences sp;

    public RtcCacheUtil(Context context) {
        sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveAppID(String appID) {
        sp.edit().putString(KEY_APP_ID, appID).apply();
    }

    public String getKeyAppId() {
        return sp.getString(KEY_APP_ID, null);
    }

    public void clearAllCache() {
        sp.edit().clear().apply();
    }
}
