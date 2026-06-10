package com.openim.tophone.rtc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class RoomKeepLifeService extends Service {
    public static final String CHANNEL_ID = "RoomKeepLifeServiceChannel";
    private static final String COMMAND = "command";
    private static final String COMMAND_START = "start";
    private static final String COMMAND_STOP = "stop";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground(new Intent());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String command = intent.getStringExtra(COMMAND);
            if (COMMAND_START.equals(command)) {
                startAsForeground(intent);
            } else if (COMMAND_STOP.equals(command)) {
                stopInternal();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public void stopInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            stopForeground(true);
            stopSelf();
        }
    }

    private void startAsForeground(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            createNotificationChannel();
            Intent notificationIntent = new Intent(this, RawAudioDataActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this,
                    0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("房间正在进行中...")
                    .setContentIntent(pendingIntent)
                    .setShowWhen(false)
                    .build();

            startForeground(110, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
