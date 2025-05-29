package com.openim.tophone.utils;

import android.content.Context;
import android.content.pm.PackageManager;

import com.openim.tophone.base.BaseApp;

public class AppUtils {
    public static int getLocalVersionCode() {
        Context context = BaseApp.inst();
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return 0;
        }
    }
}
