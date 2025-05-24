package com.openim.tophone.openim.entity;

import android.content.Context;
import android.util.Log;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.net.bage.GsonHel;
import com.openim.tophone.ui.main.MainActivity;
import com.openim.tophone.utils.L;
import com.openim.tophone.utils.SharedPreferencesUtil;

public class LoginCertificate {
    private static final String TAG = "LoginCertificate";

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getImToken() {
        return imToken;
    }

    public void setImToken(String imToken) {
        this.imToken = imToken;
    }

    public String getChatToken() {
        return chatToken;
    }

    public void setChatToken(String chatToken) {
        this.chatToken = chatToken;
    }

    public String nickname;
    public String userID;
    public String imToken;
    public String chatToken;

    public void cache(Context context) {
        Log.d(TAG, "LoginCertificate cache context:" + context);
        SharedPreferencesUtil.get(context).setCache("user.LoginCertificate",
                GsonHel.toJson(this));
    }

    public static LoginCertificate getCache(Context context) {
        String u = SharedPreferencesUtil.get(context).getString("user.LoginCertificate");
        if (u.isEmpty()) {
            Log.d(TAG, "LoginCertificate getCache null, context:" + context);
            return null;
        }
        Log.d(TAG, "LoginCertificate getCache ok. context:" + context);
        return GsonHel.fromJson(u, LoginCertificate.class);
    }

    public static void clear() {
//        MainActivity.sp.edit().clear().apply();
        SharedPreferencesUtil.remove(BaseApp.inst(),
                "user.LoginCertificate");
        L.e(TAG,"LoginCertificate 已移除");
    }

}
