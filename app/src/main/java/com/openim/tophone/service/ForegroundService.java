package com.openim.tophone.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.openim.tophone.R;

public class ForegroundService extends Service {

    private static  final String TAG ="ForegroundServiceChannel";
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private TelephonyManager telephonyManager;
    private PhoneStateListener mPhoneListener;

    @Override
    public void onCreate() {
        super.onCreate();
        initEvent();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ToPhone")
                .setContentText("Running...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        // 执行后台任务
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "ToPhone",
                NotificationManager.IMPORTANCE_HIGH
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    private void initEvent() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        // 在注册监听的时候就会走一次回调，后面通话状态改变时也会走
        // 如下面的代码，在启动服务时如果手机没有通话相关动作，就会直接走一次TelephonyManager.CALL_STATE_IDLE
        mPhoneListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                super.onCallStateChanged(state, phoneNumber);
                switch (state) {
                    // 挂断
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Toast.makeText(MyService.this, "挂断" + phoneNumber, Toast.LENGTH_SHORT).show();
                        Log.i(TAG, "onCallStateChanged: 挂断" + phoneNumber);
                        onCallFinish();
                        break;
                    // 接听
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        Log.i(TAG, "onCallStateChanged: 接听" + phoneNumber);
                        break;
                    // 响铃
                    case TelephonyManager.CALL_STATE_RINGING:
                        Log.i(TAG, "onCallStateChanged: 响铃" + phoneNumber);
                        onCalling(phoneNumber);//上报给parent
                        break;
                }
            }
        };
        telephonyManager.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    // 结束通话
    private void onCallFinish() {
        // 这里添加结束通话时的具体逻辑代码
    }

    // 被呼叫
    private void onCalling(String phoneNumber) {
        // 这里添加被呼叫时的具体逻辑代码
    }
}

