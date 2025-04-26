package com.openim.tophone.ui.main.vm;

import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.constraintlayout.widget.Group;
import androidx.databinding.ObservableField;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.base.BaseViewModel;
import com.openim.tophone.base.vm.State;
import com.openim.tophone.database.DatabaseHelper;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.net.RXRetrofit.NetObserver;
import com.openim.tophone.net.RXRetrofit.Parameter;
import com.openim.tophone.openim.entity.LoginCertificate;
import com.openim.tophone.openim.entity.OpenIMUserInfoResp;
import com.openim.tophone.repository.OneselfService;
import com.openim.tophone.repository.OpenIMService;
import com.openim.tophone.stroage.VMStore;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.L;
import com.openim.tophone.utils.OpenIMUtils;
import com.openim.tophone.utils.SharedPreferencesUtil;

import java.util.HashMap;
import java.util.List;

import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.Platform;
import io.openim.android.sdk.listener.OnAdvanceMsgListener;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.listener.OnFriendshipListener;
import io.openim.android.sdk.models.GroupInfo;
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
            login();
        }

        connectionStatus.set(!status);
        isLoading.set(false);

    }

    public void login() {
        DatabaseHelper dbHelper = new DatabaseHelper(BaseApp.inst());
        String email = dbHelper.getValueByName(Constants.DB_NAME_EMAIL);
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
                                    String userId = dbHelper.getValueByName(Constants.DB_NAME_USERID);
                                    String nickName = dbHelper.getValueByName(Constants.DB_NAME_NICKNAME);
                                    loginCertificate.userID=userId;
                                    loginCertificate.nickName=nickName;
                                    loginCertificate.cache(getContext());
                                    BaseApp.inst().loginCertificate = loginCertificate;
                                    //登陆成功后就获取群信息
                                    OpenIMUtils.updateGroupInfo();
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



    public void checkIfUserExists(String email) {

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
                            user.put("areaCode", "+853");
                            registerParameter.add("user", user).add("verifyCode", "666666").add("autoLogin", true).add("platform", Platform.ANDROID);

                            N.API(OpenIMService.class)
                                    .register(registerParameter.buildJsonBody())
                                    .map(OpenIMService.turn(LoginCertificate.class)).compose(N.IOMain()).subscribe(new NetObserver<LoginCertificate>(getContext()) {
                                                                                                                       @Override
                                                                                                                       public void onSuccess(LoginCertificate o) {
//                                                                                                                           注册成功把userID和nickname存入数据库
                                                                                                                           DatabaseHelper dbHelper = new DatabaseHelper(BaseApp.inst());
                                                                                                                           dbHelper.insertData("userId",o.userID);
                                                                                                                           dbHelper.insertData("nickName",o.nickName);
                                                                                                                           dbHelper.insertData("email",email);
                                                                                                                           accountID.set(o.nickName);
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
                            DatabaseHelper dbHelper = new DatabaseHelper(BaseApp.inst());
                            dbHelper.updateValueByName(Constants.DB_NAME_NICKNAME,userInfo.getNickname());
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
