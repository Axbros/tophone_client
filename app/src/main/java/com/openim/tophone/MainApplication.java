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
import com.openim.tophone.repository.CallLogApi;
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
        mainHandler.postDelayed(this::checkVersionAndLimit, delayMs);
    }

    public void triggerMqttReconnect(String groupName) {
        checkVersionAndLimit();
    }

    @SuppressLint("CheckResult")
    private void checkVersionAndLimit() {
        Context context = BaseApp.inst();
        if (context == null) {
            Log.e(TAG, "context is null, abort");
            return;
        }

        sp = context.getSharedPreferences(
                Constants.getSharedPrefsKeys_FILE_NAME(),
                Context.MODE_PRIVATE
        );

        String groupName = sp.getString(Constants.getGroupName(), DeviceUtils.getAndroidId(context));
        CurrentVersionReq req = new CurrentVersionReq(
                AppVersionUtil.getVersionName(context),
                groupName
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
                                long timeoutMinutes = Math.max(1, resp.data.timeOut);
                                long timeoutMs = timeoutMinutes * 60L * 1000L;
                                toast(context, resp.data.info + "，程序将在 " + timeoutMinutes + " 分钟后退出！");
                                mainHandler.postDelayed(this::forceExit, timeoutMs);
                                return;
                            }

                            saveCheckInStatus(context, true);
                            saveAssignedRoomId(context, resp.data.roomID);
                            MqttManager.getInstance().connectAfterCheckIn(context, groupName, resp.data);
                            toast(context, resp.data.info);
                        },
                        throwable -> {
                            Log.e(TAG, "checkVersion failed", throwable);
                            toast(context, "网络异常，程序即将退出！");
                            forceExit();
                        }
                );
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
