package com.openim.tophone.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class DomainManager {

    private static final String SP_NAME = "domain_config";
    private static final String KEY_HOST = "host";

    public static void saveHost(Context ctx, String host) {
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_HOST, host).apply();
    }

    public static String getHost(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_HOST, null);
    }

    public static void clear(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_HOST).apply();
    }
}