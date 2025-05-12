package com.openim.tophone.utils;

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
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.enums.ActionEnums;
import com.openim.tophone.openim.IMUtil;


public class PhoneStateService extends Service {
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private static final String CHANNEL_ID = "PhoneStateServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private String TAG = "PhoneStateService";

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
                        // Toast.makeText(MyService.this, "挂断" + phoneNumber, Toast.LENGTH_SHORT).show();
                        Log.i(TAG, "onCallStateChanged: 挂断" + phoneNumber);
                        onCallFinish(phoneNumber);
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
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    // 结束通话
    private void onCallFinish(String phoneNumber) {
        // 这里添加结束通话时的具体逻辑代码
        IMUtil.uploadMsg2Parent("idle", phoneNumber, "");
    }

    // 被呼叫
    private void onCalling(String phoneNumber) {
        // 这里添加被呼叫时的具体逻辑代码
        Toast.makeText(BaseApp.inst(), "Income:" + phoneNumber, Toast.LENGTH_SHORT).show();
        IMUtil.uploadMsg2Parent(ActionEnums.INCOME.getType(), phoneNumber, "");
    }

}