package com.openim.tophone.rtc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects USB sound cards (UAC). On standard Android they appear as USB headset/device
 * and the status bar shows the headphone icon.
 */
public final class UsbAudioDetector {

    private static final String TAG = "UsbAudioDetector";

    public interface Listener {
        void onUsbAudioChanged(boolean connected);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean usbConnected = new AtomicBoolean(false);

    @Nullable
    private Listener listener;
    @Nullable
    private AudioDeviceCallback deviceCallback;
    @Nullable
    private BroadcastReceiver headsetReceiver;
    private int registerCount;

    public UsbAudioDetector(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean isUsbAudioConnected() {
        return usbConnected.get() || scanUsbAudioDevices();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void start() {
        registerCount++;
        if (registerCount > 1) {
            notifyCurrentState();
            return;
        }
        refreshState(false);
        registerDeviceCallback();
        registerHeadsetReceiver();
    }

    public void stop() {
        registerCount = Math.max(0, registerCount - 1);
        if (registerCount > 0) {
            return;
        }
        unregisterDeviceCallback();
        unregisterHeadsetReceiver();
    }

    private void registerDeviceCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        AudioManager audioManager = appContext.getSystemService(AudioManager.class);
        if (audioManager == null) {
            return;
        }
        deviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                refreshState(true);
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                refreshState(true);
            }
        };
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler);
    }

    private void unregisterDeviceCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || deviceCallback == null) {
            deviceCallback = null;
            return;
        }
        AudioManager audioManager = appContext.getSystemService(AudioManager.class);
        if (audioManager != null) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback);
        }
        deviceCallback = null;
    }

    private void registerHeadsetReceiver() {
        if (headsetReceiver != null) {
            return;
        }
        headsetReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshState(true);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(headsetReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(headsetReceiver, filter);
        }
    }

    private void unregisterHeadsetReceiver() {
        if (headsetReceiver == null) {
            return;
        }
        try {
            appContext.unregisterReceiver(headsetReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        headsetReceiver = null;
    }

    private void refreshState(boolean notify) {
        boolean connected = scanUsbAudioDevices();
        boolean changed = usbConnected.getAndSet(connected) != connected;
        Log.i(TAG, "USB audio " + (connected ? "connected" : "disconnected"));
        if (notify && changed) {
            notifyCurrentState();
        }
    }

    private void notifyCurrentState() {
        Listener current = listener;
        if (current != null) {
            current.onUsbAudioChanged(usbConnected.get());
        }
    }

    private boolean scanUsbAudioDevices() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        AudioManager audioManager = appContext.getSystemService(AudioManager.class);
        if (audioManager == null) {
            return false;
        }
        for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_ALL)) {
            if (isUsbAudioType(device.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUsbAudioType(int type) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return type == AudioDeviceInfo.TYPE_USB_HEADSET
                    || type == AudioDeviceInfo.TYPE_USB_DEVICE;
        }
        return type == AudioDeviceInfo.TYPE_USB_ACCESSORY;
    }
}
