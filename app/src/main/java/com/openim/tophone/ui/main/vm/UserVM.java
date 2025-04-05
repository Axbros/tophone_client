package com.openim.tophone.ui.main.vm;

import android.util.Log;
import android.widget.Toast;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.base.BaseViewModel;
import com.openim.tophone.base.vm.State;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.net.RXRetrofit.NetObserver;
import com.openim.tophone.net.RXRetrofit.Parameter;
import com.openim.tophone.openim.IMEvent;
import com.openim.tophone.openim.entity.LoginCertificate;
import com.openim.tophone.openim.vm.UserLogic;
import com.openim.tophone.repository.OneselfService;
import com.openim.tophone.repository.OpenIMService;
import com.openim.tophone.utils.L;

import java.util.HashMap;

import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.Platform;
import io.openim.android.sdk.listener.OnAdvanceMsgListener;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.listener.OnFriendshipListener;

public class UserVM extends BaseViewModel implements OnAdvanceMsgListener, OnFriendshipListener {


    public State<String> getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(State<String> accountStatus) {
        this.accountStatus = accountStatus;
    }

    public State<String> getAccountID() {
        return accountID;
    }

    public void setAccountID(State<String> accountID) {
        this.accountID = accountID;
    }

    public State<String> accountID = new State<>("");
    public State<String> accountStatus = new State<>("Offline");

    public State<Boolean> getPhonePermissions() {
        return phonePermissions;
    }

    public void setPhonePermissions(State<Boolean> phonePermissions) {
        this.phonePermissions = phonePermissions;
    }

    public State<Boolean> getSmsPermissions() {
        return smsPermissions;
    }

    public void setSmsPermissions(State<Boolean> smsPermissions) {
        this.smsPermissions = smsPermissions;
    }


    public State<Boolean> getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(State<Boolean> connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public State<Boolean> phonePermissions = new State<>(false);
    public State<Boolean> smsPermissions = new State<>(false);
    public State<Boolean> connectionStatus = new State<>(false);

    public State<Boolean> isLoading = new State<>(false);


    private String TAG = "UserVM";

    public void connect() {
        isLoading.setValue(true);
        connectionStatus.setValue(true);

    }

    public void login() {

        Parameter parameter = new Parameter();
        parameter.add("email", accountID.getValue())
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
                                    accountStatus.setValue("Offline");
                                    Toast.makeText(getContext(), "LoginCertificate OpenIMClient.getInstance().login onError", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onSuccess(String data) {
                                    //缓存登录信息
                                    Log.d(TAG, "LoginCertificate OpenIMClient.getInstance().login onSuccess");
                                    accountStatus.setValue("Online");
                                    loginCertificate.cache(getContext());
                                    BaseApp.inst().loginCertificate = loginCertificate;
                                }
                            }, loginCertificate.userID, loginCertificate.imToken);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }


                });
    }

    public void checkIfUserExists(String email) {
        phonePermissions.setValue(true);

        Parameter parameter = OneselfService.buildPagination(1, 1);
        parameter.add("keyword", email).add("normal", 1);

        N.API(OneselfService.class)
                .searchUser(parameter.buildJsonBody())
                .map(OpenIMService.turn(HashMap.class)).compose(N.IOMain()).subscribe(new NetObserver<>(getContext()) {
                    @Override
                    public void onSuccess(HashMap hashMap) {
                        L.d(TAG, "current account: " + email + " Total number:" + hashMap.get("total"));
                        Integer total = (Integer) hashMap.get("total");
                        if (total == 0) {
                            L.d(TAG, "prepare to register account: " + email + "!");
                            accountStatus.setValue("Unregistered");
                            //如果没有账号那就注册！
                            Parameter registerParameter = new Parameter();
                            HashMap user = new HashMap();
                            user.put("email", email);
                            user.put("password", "516f00c9229200d6ce526991cdfdd959");
                            registerParameter.add("user", user).add("verifyCode", "666666").add("autoLogin", true).add("platform", Platform.ANDROID);

                            N.API(OpenIMService.class)
                                    .register(registerParameter.buildJsonBody())
                                    .map(OpenIMService.turn(LoginCertificate.class)).compose(N.IOMain()).subscribe(new NetObserver<LoginCertificate>(getContext()) {
                                                                                                                       @Override
                                                                                                                       public void onSuccess(LoginCertificate o) {
                                                                                                                           accountID.setValue(o.nickname);
                                                                                                                           accountStatus.setValue("Registered");
                                                                                                                           o.cache(getContext());
                                                                                                                           Toast.makeText(getContext(), "LoginCertificate register onSuccess", Toast.LENGTH_SHORT).show();
                                                                                                                       }

                                                                                                                       @Override
                                                                                                                       protected void onFailure(Throwable e) {
                                                                                                                           super.onFailure(e);
                                                                                                                           accountStatus.setValue("Register Error");
                                                                                                                           Log.d(TAG, "Register Error" + e.toString());
                                                                                                                           Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                                                                                                                       }
                                                                                                                   }
                                    );
                        }
                    }
                });
    }
}
