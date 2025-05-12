package com.openim.tophone.ui.main.vm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.databinding.ObservableField;

import com.openim.tophone.MainApplication;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.base.BaseViewModel;

import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.net.RXRetrofit.NetObserver;
import com.openim.tophone.net.RXRetrofit.Parameter;
import com.openim.tophone.openim.entity.LoginCertificate;
import com.openim.tophone.openim.entity.OpenIMUserInfoResp;
import com.openim.tophone.repository.OneselfService;
import com.openim.tophone.repository.OpenIMService;
import com.openim.tophone.ui.main.MainActivity;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.L;
import com.openim.tophone.utils.OpenIMUtils;
import com.openim.tophone.utils.SharedPreferencesUtil;


import java.util.HashMap;

import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.Platform;
import io.openim.android.sdk.listener.OnAdvanceMsgListener;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.listener.OnFriendshipListener;
import io.openim.android.sdk.models.UserInfo;

public class UserVM extends BaseViewModel implements OnAdvanceMsgListener, OnFriendshipListener {

    public ObservableField<String> accountID = new ObservableField<>("");
    public ObservableField<String> GroupInfoLabel = new ObservableField<>("Unknow");

    public ObservableField<Boolean> phonePermissions = new ObservableField<>(false);
    public ObservableField<Boolean> smsPermissions = new ObservableField<>(false);
    public ObservableField<Boolean> connectionStatus = new ObservableField<>(false);
    public ObservableField<Boolean> isLoading = new ObservableField<>(false);



    private String TAG = "UserVM";

    public void handleBtnConnect() {
        isLoading.set(true);
        Boolean status = connectionStatus.get();
        if (status) {
//            登陆状态点击按钮就给他下线
            logout();
        } else {
            login(MainActivity.getLoginEmail());
        }
        isLoading.set(false);
    }

    public void login(String machineCode) {
        String email = machineCode+"@qq.com";
        Parameter parameter = new Parameter();
        parameter.add("email", email)
                .add("password", "516f00c9229200d6ce526991cdfdd959")
                .add("platform", 2)
                .add("usedFor", 3)
                .add("operationID", System.currentTimeMillis() + "");
        N.API(OpenIMService.class)
                .login(parameter.buildJsonBody())
                .compose(N.IOMain())
                .map(OpenIMService.turn(LoginCertificate.class))
                .subscribe(new NetObserver<LoginCertificate>(getContext()) {
                    @Override
                    public void onSuccess(LoginCertificate loginCertificate) {
                        try {
                            OpenIMClient.getInstance().login(new OnBase<String>() {
                                @Override
                                public void onError(int code, String error) {
                                    GroupInfoLabel.set("Offline"+error);
                                    Toast.makeText(getContext(), "LoginCertificate OpenIMClient.getInstance().login onError", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onSuccess(String data) {
                                    //缓存登录信息
                                    Log.d(TAG, "LoginCertificate OpenIMClient.getInstance().login onSuccess");
                                    connectionStatus.set(true);
                                    loginCertificate.cache(getContext());
                                    BaseApp.inst().loginCertificate = loginCertificate;
                                    //登陆成功后就获取群信息
                                    OpenIMUtils.updateGroupInfo();
                                    connectionStatus.set(true);
                                }
                            }, loginCertificate.userID, loginCertificate.imToken);

                        } catch (Exception e) {
                            e.printStackTrace();
                            connectionStatus.set(false);
                        }
                    }


                });
    }

    public void logout(){
        LoginCertificate cert_cache=LoginCertificate.getCache(getContext());
        Parameter parameter = new Parameter();
        assert cert_cache != null;
        parameter.add("userID",cert_cache.userID);
        parameter.add("platformID",Platform.ANDROID);
        OpenIMClient.getInstance().logout(new OnBase<String>() {
            @Override
            public void onError(int code, String error) {
                OnBase.super.onError(code, error);
            }

            @Override
            public void onSuccess(String data) {
                LoginCertificate.clear();
                OnBase.super.onSuccess(data);
            }
        });



    }



    public void checkIfUserExists(String machineCode) {

        String email = machineCode+ "@qq.com";

        Parameter parameter = OneselfService.buildPagination(1, 1);
        parameter.add("keyword", email).add("normal", 1);

        N.API(OneselfService.class)
                .searchUser(parameter.buildJsonBody())
                .map(OpenIMService.turn(OpenIMUserInfoResp.class)).compose(N.IOMain()).subscribe(new NetObserver<OpenIMUserInfoResp>(getContext()) {

                    @Override
                    public void onSuccess(OpenIMUserInfoResp userInfoResp) {
                        L.d(TAG, "current account: " + email + " Total number:" + userInfoResp.total);
                        Integer total = userInfoResp.total;
                        if (total == 0) {
                            L.d(TAG, "prepare to register account: " + email + "!");
                            GroupInfoLabel.set("Unregistered");
                            //如果没有账号那就注册！
                            Parameter registerParameter = new Parameter();
                            HashMap user = new HashMap();
                            user.put("email", email);
                            user.put("password", "516f00c9229200d6ce526991cdfdd959");
                            user.put("nickname", "android");
                            user.put("areaCode", "+86");
                            registerParameter.add("user", user).add("verifyCode", "666666").add("autoLogin", true).add("platform", Platform.ANDROID);
                            //开始注册
                            N.API(OpenIMService.class)
                                    .register(registerParameter.buildJsonBody())
                                    .map(OpenIMService.turn(LoginCertificate.class)).compose(N.IOMain()).subscribe(new NetObserver<LoginCertificate>(getContext()) {
                                                                                                                       @Override
                                                                                                                       public void onSuccess(LoginCertificate o) {
//
                                                                                                                           SharedPreferences sp =BaseApp.inst().getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE);
                                                                                                                           MainActivity.sp.edit().putString(Constants.getSharedPrefsKeys_NICKNAME(),o.getNickname()).apply();

                                                                                                                           accountID.set(o.nickname);
                                                                                                                           GroupInfoLabel.set("Registered");
                                                                                                                           connectionStatus.set(true);
                                                                                                                           o.cache(getContext());
                                                                                                                           Toast.makeText(getContext(), "LoginCertificate register onSuccess", Toast.LENGTH_SHORT).show();
                                                                                                                       }

                                                                                                                       @Override
                                                                                                                       protected void onFailure(Throwable e) {
                                                                                                                           super.onFailure(e);
                                                                                                                           GroupInfoLabel.set("Register Error");
                                                                                                                           Log.d(TAG, "Register Error" + e.toString());
                                                                                                                           accountID.set(e.toString());
                                                                                                                           Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                                                                                                                       }
                                                                                                                   }
                                    );
                        }
                        else{
                            UserInfo userInfo = userInfoResp.users.get(0);
//                            每次登陆更新 登陆证书的用户名
                            MainActivity.sp.edit().putString(Constants.getSharedPrefsKeys_NICKNAME(),userInfo.getNickname()).apply();


                        }
                    }

                    @Override
                    protected void onFailure(Throwable e) {
                        super.onFailure(e);
                    }

                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);
                    }
                });
    }
}
