package com.openim.tophone.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;

import android.util.Log;
import android.widget.Toast;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.enums.ActionEnums;
import com.openim.tophone.openim.IMUtil;
import com.openim.tophone.utils.L;

public class PhoneStateService extends Service {

    // 保存服务的开启状态，相当于Kotlin中的companion object里的属性
    public static boolean live = false;
    // 对应Kotlin中的常量定义，这里定义为静态常量
    private static final int NOTIFICATION_ID = 9;

    private TelephonyManager telephonyManager;
    private PhoneStateListener mPhoneListener;
    private static final String TAG = "PhoneStateService";

    public static boolean isLive() {
        return live;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // 不需要与其他组件交互的话直接返回null即可
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initEvent();
        startForeground(NOTIFICATION_ID, createForegroundNotification());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        live = false;
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        live = true;
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 创建服务通知
     */
    private Notification createForegroundNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // 唯一的通知通道的id.
        String notificationChannelId = "notification_channel_id_01";

        // Android8.0以上的系统，新建消息通道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 用户可见的通道名称
            String channelName = "Foreground Service Notification";
            // 通道的重要程度
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel notificationChannel = new NotificationChannel(notificationChannelId, channelName, importance);
            notificationChannel.setDescription("Channel description");

            // LED灯
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            // 震动
            notificationChannel.setVibrationPattern(new long[]{0});
            notificationChannel.enableVibration(false);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, notificationChannelId);
        // 通知标题
        builder.setContentTitle("工作台运行中");

        builder.setDefaults(Notification.DEFAULT_SOUND);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        // 设定通知显示的时间
        builder.setWhen(System.currentTimeMillis());
        // 创建通知并返回
        return builder.build();
    }

    private void initEvent() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        mPhoneListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                super.onCallStateChanged(state, phoneNumber);
                L.d(TAG,"onCallStateChanged: state = " + state + ", phoneNumber = " + phoneNumber);
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
                        onCalling(phoneNumber);
                        break;
                }
            }
        };
        telephonyManager.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    // 结束通话
    private void onCallFinish() {
        // 这里添加结束通话时的具体逻辑代码
        IMUtil.uploadMsg2Parent("idle","","");
    }

    // 被呼叫
    @SuppressLint("ResourceType")
    private void onCalling(String phoneNumber) {
        // 这里添加被呼叫时的具体逻辑代码 incoming
        Toast.makeText(BaseApp.inst(), "Income:"+phoneNumber, Toast.LENGTH_SHORT).show();
        IMUtil.uploadMsg2Parent(ActionEnums.INCOME.getType(),phoneNumber,"");
    }
}