package com.openim.tophone.utils;

import android.content.Context;
public class DomainManager {

    private static final String PREF_NAME = "domain_config";
    private static final String KEY_HOST = "api_host";

    public static String getCachedHost(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_HOST, null);
    }

    public static void saveHost(Context ctx, String host) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HOST, host)
                .apply();
    }
}
