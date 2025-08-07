package com.openim.tophone.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class AppVersionUtil {

    /**
     * 获取应用的versionName
     * @param context 上下文
     * @return 应用版本名称，如"1.2.2"
     */
    public static String getVersionName(Context context) {
        String versionName = "";
        try {
            // 获取包管理器
            PackageManager packageManager = context.getPackageManager();
            // 获取包信息
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            // 获取versionName
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionName;
    }

    /**
     * 获取应用的versionCode
     * @param context 上下文
     * @return 应用版本号
     */
    public static int getVersionCode(Context context) {
        int versionCode = 0;
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);

            // 适配Android P及以上版本
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                versionCode = (int) packageInfo.getLongVersionCode();
            } else {
                versionCode = packageInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionCode;
    }
}
