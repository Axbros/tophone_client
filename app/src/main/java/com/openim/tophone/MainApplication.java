package com.openim.tophone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.openim.tophone.R;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.net.RXRetrofit.HttpConfig;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.mqtt.MqttManager;
import com.openim.tophone.openim.entity.CheckVersionResp;
import com.openim.tophone.openim.entity.CurrentVersionReq;
import com.openim.tophone.openim.entity.DeviceLoginReq;
import com.openim.tophone.openim.entity.DeviceLoginResp;
import com.openim.tophone.repository.CallLogApi;
import com.openim.tophone.repository.LoginApi;
import com.openim.tophone.stroage.VMStore;
import com.openim.tophone.utils.ActivityManager;
import com.openim.tophone.utils.AppBootstrapClient;
import com.openim.tophone.utils.AppVersionUtil;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DeviceUtils;
import com.openim.tophone.utils.L;
import com.openim.tophone.rtc.RtcBackgroundJoiner;
import com.openim.tophone.rtc.RtcCrashHandler;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Request;

public class MainApplication extends BaseApp {
    private static final String TAG = "VersionCheck";
    private static final long CHECK_VERSION_RETRY_MS = 30_000L;
    private static final long POLICY_SYNC_MS = 30_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String pendingCheckDeviceCode;
    private String activeCheckedInDeviceCode;
    private final AtomicBoolean bootstrapInFlight = new AtomicBoolean(false);
    private final AtomicBoolean policySyncInFlight = new AtomicBoolean(false);

    public static SharedPreferences sp;

    @Override
    public void onCreate() {
        super.onCreate();
        L.e(TAG, "-----onCreate------ pid=" + android.os.Process.myPid());

        Constants.initFileDir(getFilesDir().getAbsolutePath());
        RtcCrashHandler.install(this);
        initFile();
        initController();

        Constants.resolveHostFromStorage(this);
        AppBootstrapClient.applyCached(this);
        Log.i(TAG, "API host=" + Constants.getCurrentHost()
                + " baseUrl=" + Constants.getManagementUrl());

        RtcBackgroundJoiner.get().init(this);
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
    }

    /** 由 MainActivity 在界面就绪后触发（无需电话/SMS 权限即可登录） */
    public void startBootstrap() {
        mainHandler.postDelayed(this::refreshRemoteConfigAndStart, 500L);
    }

    private void refreshRemoteConfigAndStart() {
        AppBootstrapClient.refresh(this, new AppBootstrapClient.Callback() {
            @Override
            public void onComplete(boolean updated) {
                if (updated) {
                    Log.i(TAG, "remote bootstrap applied, rebuild retrofit baseUrl=" + Constants.getManagementUrl());
                    initNet();
                }
                ensureDeviceAccountAndCheckIn();
            }

            @Override
            public void onForceUpgrade(String upgradeUrl) {
                openUpgradeUrl(upgradeUrl);
            }
        });
    }

    private void openUpgradeUrl(String upgradeUrl) {
        if (upgradeUrl == null || upgradeUrl.trim().isEmpty()) {
            Toast.makeText(this, "需要更新 App，请联系管理员获取新版安装包", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(upgradeUrl.trim()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /** 设备 ID 自动注册/登录，再执行 check_version */
    @SuppressLint("CheckResult")
    private void ensureDeviceAccountAndCheckIn() {
        if (!bootstrapInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "bootstrap already in flight, skip duplicate login");
            return;
        }
        Context context = BaseApp.inst();
        if (context == null) {
            Log.e(TAG, "context is null, abort");
            bootstrapInFlight.set(false);
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
                            bootstrapInFlight.set(false);
                            if (loginResp != null && loginResp.code == 0 && loginResp.data != null) {
                                saveDeviceAccount(context, loginResp.data);
                                checkVersionAndLimit(clientDeviceId);
                            } else {
                                Log.w(TAG, "deviceLogin failed: " + (loginResp != null ? loginResp.msg : "null"));
                                toastRes(context, R.string.toast_login_failed);
                                scheduleBootstrapRetry();
                            }
                        },
                        throwable -> {
                            bootstrapInFlight.set(false);
                            Log.e(TAG, "deviceLogin error", throwable);
                            toastRes(context, R.string.toast_network_error);
                            scheduleBootstrapRetry();
                        }
                );
    }

    @SuppressLint("CheckResult")
    private void checkVersionAndLimit(String deviceCode) {
        checkVersionAndLimit(deviceCode, true);
    }

    @SuppressLint("CheckResult")
    private void checkVersionAndLimit(String deviceCode, boolean reconnectMqtt) {
        Context context = BaseApp.inst();
        if (context == null) {
            Log.e(TAG, "context is null, abort");
            return;
        }
        if (deviceCode == null || deviceCode.isEmpty()) {
            toastRes(context, R.string.toast_invalid_device_id);
            scheduleBootstrapRetry();
            return;
        }

        pendingCheckDeviceCode = deviceCode;
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
                        resp -> handleCheckVersionResponse(context, deviceCode, resp, reconnectMqtt),
                        throwable -> {
                            Log.e(TAG, "checkVersion failed", throwable);
                            toastRes(context, R.string.toast_network_error);
                            scheduleCheckVersionRetry(deviceCode, CHECK_VERSION_RETRY_MS);
                        }
                );
    }

    private void handleCheckVersionResponse(
            Context context,
            String deviceCode,
            CheckVersionResp resp,
            boolean reconnectMqtt
    ) {
        if (resp == null || resp.data == null) {
            toastRes(context, R.string.toast_version_check_invalid);
            scheduleCheckVersionRetry(deviceCode, CHECK_VERSION_RETRY_MS);
            return;
        }

        if (resp.code != 0) {
            String msg = resp.data.info != null ? resp.data.info : context.getString(R.string.toast_version_check_error);
            toast(context, msg);
            scheduleCheckVersionRetry(deviceCode, CHECK_VERSION_RETRY_MS);
            return;
        }

        applyPolicyFromCheckVersion(resp.data, reconnectMqtt);
        applyBindStateFromCheckVersion(context, resp.data);

        if (Boolean.TRUE.equals(resp.data.isExist)) {
            mainHandler.removeCallbacks(checkVersionRetryRunnable);
            pendingCheckDeviceCode = null;
            activeCheckedInDeviceCode = deviceCode;
            saveCheckInStatus(context, true);
            saveAssignedRoomID(context, resp.data.roomID);
            if (reconnectMqtt || !MqttManager.getInstance().isConnected()) {
                if (reconnectMqtt) {
                    MqttManager.getInstance().forceReconnect(context, deviceCode, resp.data);
                } else {
                    MqttManager.getInstance().connectAfterCheckIn(context, deviceCode, resp.data);
                }
            }
            RtcBackgroundJoiner.get().tryJoinWhenReady();
            schedulePolicySync();
            if (reconnectMqtt && resp.data.info != null && !resp.data.info.isEmpty()) {
                toast(context, resp.data.info);
            }
            return;
        }

        saveCheckInStatus(context, false);
        clearAssignedRoomID(context);
        RtcBackgroundJoiner.get().leaveRoom();
        activeCheckedInDeviceCode = null;
        stopPolicySync();
        MqttManager.getInstance().disconnect();
        String msg = resp.data.info != null ? resp.data.info : context.getString(R.string.toast_waiting);
        toast(context, msg);

        scheduleCheckVersionRetry(deviceCode, CHECK_VERSION_RETRY_MS);
    }

    private void scheduleBootstrapRetry() {
        mainHandler.removeCallbacks(bootstrapRetryRunnable);
        mainHandler.postDelayed(bootstrapRetryRunnable, CHECK_VERSION_RETRY_MS);
    }

    private final Runnable bootstrapRetryRunnable = this::ensureDeviceAccountAndCheckIn;

    private void scheduleCheckVersionRetry(String deviceCode, long delayMs) {
        if (deviceCode == null || deviceCode.isEmpty()) {
            return;
        }
        pendingCheckDeviceCode = deviceCode;
        mainHandler.removeCallbacks(checkVersionRetryRunnable);
        mainHandler.postDelayed(checkVersionRetryRunnable, delayMs);
    }

    private final Runnable checkVersionRetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (pendingCheckDeviceCode != null && !pendingCheckDeviceCode.isEmpty()) {
                checkVersionAndLimit(pendingCheckDeviceCode, true);
            }
        }
    };

    private final Runnable policySyncRunnable = new Runnable() {
        @Override
        public void run() {
            syncPolicyFromServer();
            schedulePolicySync();
        }
    };

    private void schedulePolicySync() {
        if (activeCheckedInDeviceCode == null || activeCheckedInDeviceCode.isEmpty()) {
            return;
        }
        mainHandler.removeCallbacks(policySyncRunnable);
        mainHandler.postDelayed(policySyncRunnable, POLICY_SYNC_MS);
    }

    private void stopPolicySync() {
        mainHandler.removeCallbacks(policySyncRunnable);
    }

    @SuppressLint("CheckResult")
    private void syncPolicyFromServer() {
        if (activeCheckedInDeviceCode == null || activeCheckedInDeviceCode.isEmpty()) {
            return;
        }
        if (!policySyncInFlight.compareAndSet(false, true)) {
            return;
        }
        Context context = BaseApp.inst();
        if (context == null) {
            policySyncInFlight.set(false);
            return;
        }
        String deviceCode = activeCheckedInDeviceCode;
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
                            policySyncInFlight.set(false);
                            if (resp != null && resp.code == 0 && resp.data != null) {
                                applyPolicyFromCheckVersion(resp.data, true);
                                if (!MqttManager.getInstance().isConnected()) {
                                    MqttManager.getInstance().connectAfterCheckIn(context, deviceCode, resp.data);
                                }
                            }
                        },
                        throwable -> {
                            policySyncInFlight.set(false);
                            Log.w(TAG, "policy sync failed", throwable);
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
        refreshCheckVersionOnly(true);
    }

    /** 权限授予后重新 check_version，上报含手机号的设备指纹 */
    public void triggerDeviceProfileRefresh() {
        if (activeCheckedInDeviceCode != null && !activeCheckedInDeviceCode.isEmpty()) {
            syncPolicyFromServer();
            return;
        }
        refreshCheckVersionOnly();
    }

    private void refreshCheckVersionOnly() {
        refreshCheckVersionOnly(false);
    }

    private void refreshCheckVersionOnly(boolean reconnectMqtt) {
        Context context = BaseApp.inst();
        if (context == null) {
            return;
        }
        String clientDeviceId = DeviceUtils.getOrCreateClientDeviceId(context);
        if (clientDeviceId == null || clientDeviceId.isEmpty()) {
            return;
        }
        checkVersionAndLimit(clientDeviceId, reconnectMqtt);
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

    private void applyBindStateFromCheckVersion(Context context, com.openim.tophone.openim.entity.CheckVersionDataResp data) {
        if (data == null) {
            return;
        }
        try {
            if (!VMStore.isInitialized()) {
                return;
            }
            if (Boolean.TRUE.equals(data.isExist)) {
                String groupName = data.groupName != null ? data.groupName.trim() : "";
                persistGroupName(context, groupName);
                VMStore.get().updateBindState(false, groupName);
                return;
            }
            if (Boolean.TRUE.equals(data.waitAssign)) {
                persistGroupName(context, "");
                VMStore.get().updateBindState(true, "");
                return;
            }
            if (Boolean.TRUE.equals(data.waitCheckIn)) {
                String groupName = data.groupName != null ? data.groupName.trim() : "";
                persistGroupName(context, groupName);
                VMStore.get().updateBindState(false, groupName);
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private void persistGroupName(Context context, String groupName) {
        context.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .edit()
                .putString(Constants.getGroupName(), groupName != null ? groupName : "")
                .apply();
        if (groupName != null && !groupName.isEmpty()) {
            notifyGroupName(groupName);
        }
    }

    private void applyPolicyFromCheckVersion(
            com.openim.tophone.openim.entity.CheckVersionDataResp data,
            boolean notify
    ) {
        if (data == null || (data.voiceDisabled == null && data.smsDisabled == null && data.status == null)) {
            return;
        }
        boolean voiceDisabled = Boolean.TRUE.equals(data.voiceDisabled);
        boolean smsDisabled = Boolean.TRUE.equals(data.smsDisabled);
        int status = data.status != null ? data.status : 1;
        try {
            VMStore.get().applyDevicePolicy(voiceDisabled, smsDisabled, status, notify);
        } catch (IllegalStateException ignored) {
        }
    }

    private void saveAssignedRoomID(Context context, String roomID) {
        if (roomID == null || roomID.trim().isEmpty()) {
            clearAssignedRoomID(context);
            return;
        }
        context.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .edit()
                .putString(Constants.getAssignedRoomIDKey(), roomID.trim())
                .apply();
    }

    private void clearAssignedRoomID(Context context) {
        context.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .edit()
                .remove(Constants.getAssignedRoomIDKey())
                .apply();
    }

    private void toast(Context context, String msg) {
        if (context == null) return;
        Toast.makeText(context, msg != null ? msg : "", Toast.LENGTH_LONG).show();
    }

    private void toastRes(Context context, int resId) {
        if (context == null) return;
        Toast.makeText(context, resId, Toast.LENGTH_LONG).show();
    }

    public void offline() {
        ActivityManager.finishAllExceptActivity();
    }

    public void initService() {
    }
}
