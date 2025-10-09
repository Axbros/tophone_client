package com.openim.tophone.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.enums.ActionEnums;
import com.openim.tophone.enums.CallLogType;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.openim.IMUtil;
import com.openim.tophone.repository.LocationService;

import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;
public class PhoneStateService extends Service {
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private long startTime = 0;
    private long endTime = 0;
    private boolean isCallConnected = false;  // 用于标识电话是否已接通

    private static final String CHANNEL_ID = "PhoneStateServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private String TAG = "PhoneStateService";

    private static final String API_KEY = "819bb34ae3ff372bae58d900877443d5";
    private static final String API_ID = "10004275";

    private final CallBlocker callBlocker = new CallBlocker(BaseApp.inst());

    private final PhoneUtils phoneUtils = new PhoneUtils();

    public PhoneStateService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                super.onCallStateChanged(state, phoneNumber);
                if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                    Log.e(TAG, "phoneNumber is null or empty");
                    Toast.makeText(BaseApp.inst(), "监听到通话信息，但是由于没有权限无法获取电话号码，上报取消，请检查权限问题！", Toast.LENGTH_LONG).show();
                    return;
                }

                switch (state) {
                    // 挂断
                    case TelephonyManager.CALL_STATE_IDLE:
                        // 如果通话已接通，计算时长
                        if (isCallConnected) {
                            endTime = System.currentTimeMillis();
                            long duration = (endTime - startTime) / 1000; // 通话时长，单位：秒
                            Log.d("Call", "通话时长：" + duration + "秒");
                            Toast.makeText(BaseApp.inst(),phoneNumber+"---->通话时长(秒)："+duration,Toast.LENGTH_LONG).show();
                            // 结束通话处理
                            onCallFinish(phoneNumber, duration);

                        }
                        startTime = 0; // 重置起始时间
                        isCallConnected = false; // 重置电话接通状态
                        Log.i(TAG, "onCallStateChanged: 挂断" + phoneNumber);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            CallLogUtils callLogUtils = new CallLogUtils();
                            callLogUtils.uploadLatestCallLog();  // 在这里处理 call log
                        }, 2000); // 2000 毫秒 = 2 秒
                        break;

                    // 接听
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        // 只有当电话接通时才开始计时
                        if (!isCallConnected) {
                            startTime = System.currentTimeMillis();
                            isCallConnected = true;
                            Log.i(TAG, "onCallStateChanged: 接听" + phoneNumber);
                        }
                        break;

                    // 响铃
                    case TelephonyManager.CALL_STATE_RINGING:
//                        Log.i(TAG, "onCallStateChanged: 响铃" + phoneNumber);
                        if(callBlocker.isPhoneNumberBlocked(phoneNumber)){
                            phoneUtils.hangUpCall();
                            return;
                        }
                        onCalling(phoneNumber); // 上报来电归属地
                        sendCallLogToActivity(phoneNumber,CallLogType.CALL_IN.getDescription());
                        break;
                }
            }
        };
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
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
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("正在运行")
                .setContentText("监控通话状态")
                .setSmallIcon(android.R.drawable.sym_action_call)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        N.clearDispose(this);
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    // 结束通话
    private void onCallFinish(String phoneNumber, long duration) {
        // 在这里执行结束通话后的具体操作，比如上报或存储通话时长
        Log.d("Call", "结束通话，通话时长：" + duration + "秒");
        IMUtil.uploadMsg2Parent("idle", phoneNumber, String.valueOf(duration));
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
                        location = "中國·大陸";
                    }
                    Log.d("Chat", "归属地：" + location);
                    Toast.makeText(BaseApp.inst(), "归属地：" + location, Toast.LENGTH_SHORT).show();
                    IMUtil.uploadMsg2Parent(ActionEnums.INCOME.getType(), phoneNumber, location);
                }, throwable -> {
                    Log.e("Chat", "获取归属地失败: " + throwable.getMessage());
                    Toast.makeText(BaseApp.inst(), "获取归属地失败", Toast.LENGTH_SHORT).show();
                    IMUtil.uploadMsg2Parent(ActionEnums.INCOME.getType(), phoneNumber, "");
                });
        N.addDispose(this.getClass().getSimpleName(), disposable);
    }

    private void sendCallLogToActivity(String number, String type) {
        Intent intent = new Intent("CALL_LOG_EVENT");
        intent.putExtra("number", number);
        intent.putExtra("type", type);
        sendBroadcast(intent); // 发送广播
    }
}
