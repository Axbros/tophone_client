package com.openim.tophone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.net.RXRetrofit.HttpConfig;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.mqtt.MqttManager;
import com.openim.tophone.openim.entity.CurrentVersionReq;
import com.openim.tophone.openim.entity.DeviceLoginReq;
import com.openim.tophone.openim.entity.DeviceLoginResp;
import com.openim.tophone.repository.CallLogApi;
import com.openim.tophone.repository.LoginApi;
import com.openim.tophone.stroage.VMStore;
import com.openim.tophone.ui.main.MainActivity;
import com.openim.tophone.utils.ActivityManager;
import com.openim.tophone.utils.AppVersionUtil;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DeviceUtils;
import com.openim.tophone.utils.DomainManager;
import com.openim.tophone.utils.L;

import java.io.File;

import okhttp3.Request;

public class MainApplication extends BaseApp {
    private static final String TAG = "VersionCheck";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static SharedPreferences sp;

    @Override
    public void onCreate() {
        super.onCreate();
        L.e(TAG, "-----onCreate------ pid=" + android.os.Process.myPid());

        Constants.initFileDir(getFilesDir().getAbsolutePath());
        initFile();
        initController();

        String cached = DomainManager.getHost(this);
        if (cached != null && !cached.isEmpty()) {
            Constants.updateHost(cached);
        }

        initNet();
        initService();
    }

    private void initFile() {
        buildDirectory(Constants.getFileDir());
    }

    private boolean buildDirectory(String path) {
        File file = new File(path);
        if (file.exists()) return true;
        return file.mkdirs();
    }

    private void initController() {
    }

    @SuppressLint("CheckResult")
    public void initNet() {
        Log.d(TAG, "initNet called, baseUrl=" + Constants.getManagementUrl());

        N.init(new HttpConfig()
                .setBaseUrl(Constants.getManagementUrl())
                .setDebug((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                .addInterceptor(chain -> {
                    Request request = chain.request().newBuilder()
                            .addHeader("operationID", String.valueOf(System.currentTimeMillis()))
                            .build();
                    Log.d(TAG, "HTTP -> " + request.method() + " " + request.url());
                    return chain.proceed(request);
                })
        );

        long delayMs = 1L * 1000L;
        mainHandler.postDelayed(this::ensureDeviceAccountAndCheckIn, delayMs);
    }

    /** 设备 ID 自动注册/登录，再执行 check_version */
    @SuppressLint("CheckResult")
    private void ensureDeviceAccountAndCheckIn() {
        Context context = BaseApp.inst();
        if (context == null) {
            Log.e(TAG, "context is null, abort");
            return;
        }

        sp = context.getSharedPreferences(
                Constants.getSharedPrefsKeys_FILE_NAME(),
                Context.MODE_PRIVATE
        );

        String clientDeviceId = DeviceUtils.getOrCreateClientDeviceId(context);
        DeviceLoginReq loginReq = new DeviceLoginReq(
                DeviceUtils.collectProfile(context, clientDeviceId)
        );

        N.mAPI(LoginApi.class)
                .deviceLogin(loginReq)
                .compose(N.IOMain())
                .subscribe(
                        loginResp -> {
                            if (loginResp != null && loginResp.code == 0 && loginResp.data != null) {
                                saveDeviceAccount(context, loginResp.data);
                                checkVersionAndLimit(clientDeviceId);
                            } else {
                                Log.w(TAG, "deviceLogin failed: " + (loginResp != null ? loginResp.msg : "null"));
                                toast(context, "设备登录失败，程序即将退出");
                                forceExit();
                            }
                        },
                        throwable -> {
                            Log.e(TAG, "deviceLogin error", throwable);
                            toast(context, "网络异常，程序即将退出");
                            forceExit();
                        }
                );
    }

    @SuppressLint("CheckResult")
    private void checkVersionAndLimit(String deviceCode) {
        Context context = BaseApp.inst();
        if (context == null) {
            Log.e(TAG, "context is null, abort");
            return;
        }
        if (deviceCode == null || deviceCode.isEmpty()) {
            toast(context, "设备 ID 无效，程序即将退出");
            forceExit();
            return;
        }

        sp = context.getSharedPreferences(
                Constants.getSharedPrefsKeys_FILE_NAME(),
                Context.MODE_PRIVATE
        );

        CurrentVersionReq req = new CurrentVersionReq(
                AppVersionUtil.getVersionName(context),
                deviceCode,
                DeviceUtils.collectProfile(context, deviceCode)
        );

        N.mAPI(CallLogApi.class)
                .checkCurrentVersion(req)
                .compose(N.IOMain())
                .subscribe(
                        resp -> {
                            if (resp == null || resp.data == null) {
                                toast(context, "版本检测返回异常，程序即将退出！");
                                forceExit();
                                return;
                            }

                            if (resp.code != 0) {
                                toast(context, resp.data.info);
                                forceExit();
                                return;
                            }

                            if (!resp.data.isExist) {
                                saveCheckInStatus(context, false);
                                clearAssignedRoomId(context);
                                MqttManager.getInstance().disconnect();
                                String msg = resp.data.info != null ? resp.data.info : "等待中";
                                if (resp.data.timeOut != null && resp.data.timeOut > 0) {
                                    long timeoutMinutes = Math.max(1, resp.data.timeOut);
                                    long timeoutMs = timeoutMinutes * 60L * 1000L;
                                    toast(context, msg + "，程序将在 " + timeoutMinutes + " 分钟后退出！");
                                    mainHandler.postDelayed(this::forceExit, timeoutMs);
                                } else {
                                    toast(context, msg);
                                    forceExit();
                                }
                                return;
                            }

                            saveCheckInStatus(context, true);
                            saveAssignedRoomId(context, resp.data.roomID);
                            MqttManager.getInstance().connectAfterCheckIn(context, deviceCode, resp.data);
                            toast(context, resp.data.info);
                        },
                        throwable -> {
                            Log.e(TAG, "checkVersion failed", throwable);
                            toast(context, "网络异常，程序即将退出！");
                            forceExit();
                        }
                );
    }

    private void saveDeviceAccount(Context context, DeviceLoginResp.DeviceLoginData data) {
        if (data == null) {
            return;
        }
        String displayName = data.username != null ? data.username.trim() : "";
        String deviceCode = data.deviceCode != null && !data.deviceCode.trim().isEmpty()
                ? data.deviceCode.trim()
                : (data.userID != null ? data.userID.trim() : "");
        String groupName = data.groupName != null ? data.groupName.trim() : "";
        context.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .edit()
                .putString(Constants.getNormalUsernameKey(), displayName)
                .putString(Constants.getNormalUserIDKey(), deviceCode)
                .putString(Constants.getGroupName(), groupName)
                .apply();
        notifyAccountUsername(displayName);
        if (!groupName.isEmpty()) {
            notifyGroupName(groupName);
        }
    }

    private void notifyGroupName(String groupName) {
        try {
            VMStore.get().groupInfoLabel.setValue(groupName);
        } catch (IllegalStateException ignored) {
        }
    }

    private void notifyAccountUsername(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        try {
            VMStore.get().accountID.setValue(username);
        } catch (IllegalStateException ignored) {
        }
    }

    public void triggerMqttReconnect(String groupName) {
        ensureDeviceAccountAndCheckIn();
    }

    /** 权限授予后重新 check_version，上报含手机号的设备指纹 */
    public void triggerDeviceProfileRefresh() {
        ensureDeviceAccountAndCheckIn();
    }

    private void saveCheckInStatus(Context context, boolean checkedIn) {
        context.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .edit()
                .putBoolean(Constants.getCheckedInKey(), checkedIn)
                .apply();
        notifyCheckInStatus(checkedIn);
    }

    private void notifyCheckInStatus(boolean checkedIn) {
        try {
            VMStore.get().checkedIn.setValue(checkedIn);
        } catch (IllegalStateException ignored) {
        }
    }

    private void saveAssignedRoomId(Context context, String roomID) {
        if (roomID == null || roomID.trim().isEmpty()) {
            clearAssignedRoomId(context);
            return;
        }
        context.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .edit()
                .putString(Constants.getAssignedRoomIdKey(), roomID.trim())
                .apply();
    }

    private void clearAssignedRoomId(Context context) {
        context.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .edit()
                .remove(Constants.getAssignedRoomIdKey())
                .apply();
    }

    private void toast(Context context, String msg) {
        if (context == null) return;
        Toast.makeText(context, msg != null ? msg : "", Toast.LENGTH_LONG).show();
    }

    private void forceExit() {
        MainActivity.seBtnConnectDisable();
        System.exit(0);
    }

    public void offline() {
        ActivityManager.finishAllExceptActivity();
    }

    public void initService() {
    }
}
