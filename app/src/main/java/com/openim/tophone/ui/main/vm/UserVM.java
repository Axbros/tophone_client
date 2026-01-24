package com.openim.tophone.ui.main.vm;

import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.MutableLiveData;

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
import com.openim.tophone.utils.OpenIMUtils;

import java.util.HashMap;

import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.Platform;
import io.openim.android.sdk.listener.OnAdvanceMsgListener;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.listener.OnFriendshipListener;
public class UserVM extends BaseViewModel implements OnAdvanceMsgListener, OnFriendshipListener {
    public MutableLiveData<String> accountID = new MutableLiveData<>("");
    public MutableLiveData<String> groupInfoLabel = new MutableLiveData<>("Unknown");
    public MutableLiveData<Boolean> phonePermissions = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> smsPermissions = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> connectionStatus = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public MutableLiveData<Boolean> isGroupInfoVisible = new MutableLiveData<>(true);

    private static final String DEFAULT_PASSWORD = "516f00c9229200d6ce526991cdfdd959";
    private static final String DEFAULT_NICKNAME = "android";
    private static final String EMAIL_SUFFIX = "@tsinghua.edu.cn";
    private static final String VERIFY_CODE = "666666";
    private final String TAG = "UserVM";
    public void handleBtnConnect() {
        isLoading.setValue(true);
        boolean status = Boolean.TRUE.equals(connectionStatus.getValue());
        if (status) {
            logout();
        } else {
            login(MainActivity.getLoginEmail());
        }
        isLoading.setValue(false);
    }
    public void login(String machineCode) {
        String email = machineCode + EMAIL_SUFFIX;
        Parameter parameter = createLoginParameter(email);
        N.API(OpenIMService.class)
                .login(parameter.buildJsonBody())
                .compose(N.IOMain())
                .map(OpenIMService.turn(LoginCertificate.class))
                .subscribe(new NetObserver<LoginCertificate>(BaseApp.inst()) {
                    @Override
                    public void onSuccess(LoginCertificate loginCertificate) {
                        handleLoginSuccess(loginCertificate);
                    }
                    @Override
                    public void onError(Throwable e) {
                        handleLoginError(e);
                        login(machineCode);
                    }
                });

    }
    private Parameter createLoginParameter(String email) {
        Parameter parameter = new Parameter();
        parameter.add("email", email)
                .add("password", DEFAULT_PASSWORD)
                .add("platform", 2)
                .add("usedFor", 3)
                .add("operationID", System.currentTimeMillis() + "");
        return parameter;
    }
    private void handleLoginSuccess(LoginCertificate loginCertificate) {
        try {
            OpenIMClient.getInstance().login(new OnBase<String>() {
                @Override
                public void onSuccess(String data) {
                    onLoginSuccess(loginCertificate);
                }
                @Override
                public void onError(int code, String error) {
                    onLoginError(error);
                }
            }, loginCertificate.userID, loginCertificate.imToken);
        } catch (Exception e) {
            connectionStatus.setValue(false);
            e.printStackTrace();
        }
    }
    private void onLoginSuccess(LoginCertificate loginCertificate) {
        connectionStatus.setValue(true);
        isGroupInfoVisible.setValue(true);
        loginCertificate.cache(BaseApp.inst());
        BaseApp.inst().loginCertificate = loginCertificate;
        OpenIMUtils.updateGroupInfo();
    }
    private void onLoginError(String error) {
        groupInfoLabel.setValue("Offline: " + error);
        isGroupInfoVisible.setValue(false);
        connectionStatus.setValue(false);
        logout();
        Toast.makeText(BaseApp.inst(), error, Toast.LENGTH_SHORT).show();
    }
    private void handleLoginError(Throwable e) {
        connectionStatus.setValue(false);
        Log.d("UserVM", "Login failed", e);
        System.exit(0);
    }
    public void logout() {
        LoginCertificate certCache = LoginCertificate.getCache(BaseApp.inst());

        String userID = certCache!=null ? certCache.userID : accountID.getValue();
        Parameter parameter = new Parameter();
        parameter.add("userID", userID)
                .add("platformID", Platform.ANDROID);
        OpenIMClient.getInstance().logout(new OnBase<String>() {
            @Override
            public void onSuccess(String data) {
                LoginCertificate.clear();
                connectionStatus.setValue(false);
                groupInfoLabel.setValue("👋");
                isGroupInfoVisible.setValue(false);
            }
            @Override
            public void onError(int code, String error) {
                connectionStatus.setValue(false);
                groupInfoLabel.setValue("👋");
                isGroupInfoVisible.setValue(false);
            }
        });
    }
    public void checkIfUserExists(String machineCode) {
        String email = machineCode + EMAIL_SUFFIX;
        Parameter parameter = OneselfService.buildPagination(1, 1);
        parameter.add("keyword", email).add("normal", 1);
        N.API(OneselfService.class)
                .searchUser(parameter.buildJsonBody())
                .map(OpenIMService.turn(OpenIMUserInfoResp.class))
                .compose(N.IOMain())
                .subscribe(new NetObserver<OpenIMUserInfoResp>(BaseApp.inst()) {
                    @Override
                    public void onSuccess(OpenIMUserInfoResp userInfoResp) {
                        handleUserExistence(userInfoResp, email);
                    }
                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);
                    }
                });
    }
    private void handleUserExistence(OpenIMUserInfoResp userInfoResp, String email) {
        if (userInfoResp.total == 0) {
            registerUser(email);
        } else {
            updateUserInfo(userInfoResp.users.get(0).getNickname());
        }
    }
    private void registerUser(String email) {
        Parameter registerParameter = createRegisterParameter(email);
        N.API(OpenIMService.class)
                .register(registerParameter.buildJsonBody())
                .map(OpenIMService.turn(LoginCertificate.class))
                .compose(N.IOMain())
                .subscribe(new NetObserver<LoginCertificate>(BaseApp.inst()) {
                    @Override
                    public void onSuccess(LoginCertificate loginCertificate) {
                        handleRegisterSuccess(loginCertificate);
                    }
                    @Override
                    public void onFailure(Throwable e) {
                        super.onFailure(e);
                        groupInfoLabel.setValue("Register Error");
                        Log.d(TAG, "Register Error: " + e.toString());
                    }
                });
    }
    private Parameter createRegisterParameter(String email) {
        Parameter registerParameter = new Parameter();
        HashMap<String, String> user = new HashMap<>();
        user.put("email", email);
        user.put("password", DEFAULT_PASSWORD);
        user.put("nickname", DEFAULT_NICKNAME);
        user.put("areaCode", "+86");
        registerParameter.add("user", user)
                .add("verifyCode", VERIFY_CODE)
                .add("autoLogin", true)
                .add("platform", Platform.ANDROID);
        return registerParameter;
    }
    private void handleRegisterSuccess(LoginCertificate loginCertificate) {
        MainActivity.sp.edit().putString(Constants.getSharedPrefsKeys_NICKNAME(), loginCertificate.getNickname()).apply();
        accountID.setValue(loginCertificate.nickname);
        groupInfoLabel.setValue("Registered");
        connectionStatus.setValue(true);
        loginCertificate.cache(BaseApp.inst());
        Toast.makeText(BaseApp.inst(), "Registration Successful", Toast.LENGTH_SHORT).show();
    }
    private void updateUserInfo(String nickname) {
        MainActivity.sp.edit().putString(Constants.getSharedPrefsKeys_NICKNAME(), nickname).apply();
        accountID.setValue(nickname);
    }
}
