package com.openim.tophone.utils;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.openim.tophone.R;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.enums.ActionEnums;
import com.openim.tophone.rtc.RtcSessionController;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.utils.MqttEventUtil;
import com.openim.tophone.repository.LocationService;

import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;
public class PhoneStateService extends Service {
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private long startTime = 0;
    private long endTime = 0;
    private boolean isCallConnected = false;  // 用于标识电话是否已接通
    private boolean isRinging = false;
    private long callSessionStartedAt = 0;
    private String lastKnownPhoneNumber = "";
    private final Handler callLogHandler = new Handler(Looper.getMainLooper());
    private static volatile String pendingOutgoingNumber = "";
    private static volatile long pendingOutgoingStartedAt = 0;

    private static final String CHANNEL_ID = "PhoneStateServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private String TAG = "PhoneStateService";

    private static final String API_KEY = "819bb34ae3ff372bae58d900877443d5";
    private static final String API_ID = "10004275";

    private final CallBlocker callBlocker = new CallBlocker(BaseApp.inst());

    private final PhoneUtils phoneUtils = new PhoneUtils();

    public PhoneStateService() {
    }

    public static void noteOutgoingCall(String phoneNumber) {
        pendingOutgoingNumber = phoneNumber == null ? "" : phoneNumber.trim();
        pendingOutgoingStartedAt = System.currentTimeMillis();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                super.onCallStateChanged(state, phoneNumber);
                String callbackNumber = phoneNumber == null ? "" : phoneNumber.trim();
                if (!callbackNumber.isEmpty()) {
                    lastKnownPhoneNumber = callbackNumber;
                } else if (!pendingOutgoingNumber.isEmpty()) {
                    lastKnownPhoneNumber = pendingOutgoingNumber;
                }
                String effectiveNumber = lastKnownPhoneNumber;

                switch (state) {
                    // 挂断
                    case TelephonyManager.CALL_STATE_IDLE:
                        boolean observedCall = isCallConnected || isRinging || callSessionStartedAt > 0;
                        long sessionStartedAt = callSessionStartedAt;
                        if (isCallConnected && startTime > 0) {
                            endTime = System.currentTimeMillis();
                            long duration = (endTime - startTime) / 1000;
                            Log.d("Call", "通话时长：" + duration + "秒");
                            if (!effectiveNumber.isEmpty()) {
                                AppToast.show(BaseApp.inst(),
                                        BaseApp.inst().getString(
                                                R.string.toast_call_duration_with_number,
                                                effectiveNumber,
                                                duration
                                        ),
                                        Toast.LENGTH_LONG);
                            }
                            onCallFinish(effectiveNumber, duration);
                        } else if (isRinging) {
                            // 响铃中挂断/未接（模拟器 cancel、拒接等）
                            Log.i(TAG, "onCallStateChanged: 响铃结束未接通 " + effectiveNumber);
                            MqttEventUtil.publishEvent(ActionEnums.IDLE.getType(), effectiveNumber, "");
                        }
                        if (observedCall) {
                            notifyRtcPhoneCallActive(false);
                        }
                        RtcSessionController.getInstance().onPhoneCallStateChanged(false);
                        if (observedCall) {
                            scheduleCallLogUpload(
                                    sessionStartedAt > 0 ? sessionStartedAt : System.currentTimeMillis(),
                                    effectiveNumber
                            );
                        }
                        startTime = 0;
                        endTime = 0;
                        callSessionStartedAt = 0;
                        isCallConnected = false;
                        isRinging = false;
                        lastKnownPhoneNumber = "";
                        pendingOutgoingNumber = "";
                        pendingOutgoingStartedAt = 0;
                        Log.i(TAG, "onCallStateChanged: 挂断 " + effectiveNumber);
                        break;

                    // 接听
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        if (callSessionStartedAt == 0) {
                            callSessionStartedAt = pendingOutgoingStartedAt > 0
                                    ? pendingOutgoingStartedAt
                                    : System.currentTimeMillis();
                        }
                        // 只有当电话接通时才开始计时
                        if (!isCallConnected) {
                            startTime = System.currentTimeMillis();
                            isCallConnected = true;
                            Log.i(TAG, "onCallStateChanged: 接听 " + effectiveNumber);
                        }
                        notifyRtcPhoneCallActive(true);
                        RtcSessionController.getInstance().onPhoneCallStateChanged(true);
                        break;

                    // 响铃
                    case TelephonyManager.CALL_STATE_RINGING:
                        boolean firstRingingCallback = !isRinging;
                        isRinging = true;
                        if (callSessionStartedAt == 0) {
                            callSessionStartedAt = System.currentTimeMillis();
                        }
                        pendingOutgoingNumber = "";
                        pendingOutgoingStartedAt = 0;
                        if (!firstRingingCallback) {
                            break;
                        }
                        if (effectiveNumber.isEmpty()) {
                            Log.w(TAG, "incoming number unavailable; keep tracking call state");
                            break;
                        }
                        if(callBlocker.isPhoneNumberBlocked(effectiveNumber)){
                            phoneUtils.hangUpCall();
                            return;
                        }
                        onCalling(effectiveNumber); // 上报来电归属地
                        break;
                }
            }
        };
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        registerPhoneStateListenerIfAllowed();
    }

    private void registerPhoneStateListenerIfAllowed() {
        if (telephonyManager == null || phoneStateListener == null) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE not granted, skip phone state listener");
            return;
        }
        try {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (SecurityException e) {
            Log.e(TAG, "listen call state denied: " + e.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Phone State Channel",
                    NotificationManager.IMPORTANCE_LOW // 避免显示弹窗/声音
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            createNotificationChannel();

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Running")
                    .setContentText("Monitoring call state")
                    .setSmallIcon(android.R.drawable.sym_action_call)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }
        registerPhoneStateListenerIfAllowed();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        N.clearDispose(this);
        callLogHandler.removeCallbacksAndMessages(null);
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    // 结束通话
    private void onCallFinish(String phoneNumber, long duration) {
        Log.d("Call", "结束通话，状态计时：" + duration + "秒，最终时长等待系统通话记录");
        MqttEventUtil.publishEvent("idle", phoneNumber, "");
    }

    // 被呼叫
    private void onCalling(String phoneNumber) {
        // 这里是获取归属地的逻辑，非通话时长
        Log.d("Chat", "正在获取 " + phoneNumber + " 的归属地…");
        Disposable disposable = N.API(LocationService.class)
                .getPhoneNumberLocation(API_ID, API_KEY, phoneNumber)
                .timeout(3, TimeUnit.SECONDS) // ⏱ 设置最大等待时间 3 秒
                .compose(N.IOMain())
                .subscribe(response -> {
                    String location;
                    if (response.code == 200) {
                        location = response.shengfen + "·" + response.chengshi + "·" + response.fuwushang;
                    } else {
                        location = "China Mainland";
                    }
                    Log.d("Chat", "Caller location: " + location);
                    AppToast.show(BaseApp.inst(),
                            BaseApp.inst().getString(R.string.toast_incoming_location, location),
                            Toast.LENGTH_SHORT);
                    MqttEventUtil.publishEvent(ActionEnums.INCOME.getType(), phoneNumber, location);
                }, throwable -> {
                    Log.e("Chat", "获取归属地失败: " + throwable.getMessage());
                    AppToast.show(BaseApp.inst(), R.string.toast_location_failed, Toast.LENGTH_SHORT);
                    MqttEventUtil.publishEvent(ActionEnums.INCOME.getType(), phoneNumber, "");
                });
        N.addDispose(this.getClass().getSimpleName(), disposable);
    }

    private void scheduleCallLogUpload(long sessionStartedAt, String phoneNumber) {
        final int[] attempts = {0};
        Runnable task = new Runnable() {
            @Override
            public void run() {
                attempts[0]++;
                boolean matched = new CallLogUtils()
                        .uploadLatestCallLog(sessionStartedAt, phoneNumber);
                if (!matched && attempts[0] < 4) {
                    callLogHandler.postDelayed(this, 1500L * attempts[0]);
                }
            }
        };
        callLogHandler.postDelayed(task, 1500L);
    }

    private void notifyRtcPhoneCallActive(boolean active) {
        Intent intent = new Intent(RtcSessionController.ACTION_PHONE_CALL_STATE);
        intent.putExtra(RtcSessionController.EXTRA_PHONE_CALL_ACTIVE, active);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
}
