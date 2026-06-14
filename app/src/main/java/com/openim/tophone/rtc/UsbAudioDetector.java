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

    /** USB sound card or wired headset (status bar headphone icon). */
    public boolean isHeadsetModeActive() {
        return usbConnected.get() || scanHeadsetAudioDevices();
    }

    /** @deprecated use {@link #isHeadsetModeActive()} */
    public boolean isUsbAudioConnected() {
        return isHeadsetModeActive();
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
        boolean connected = scanHeadsetAudioDevices();
        boolean changed = usbConnected.getAndSet(connected) != connected;
        RtcDebugLog.i(TAG, "headset mode " + (connected ? "active" : "inactive"));
        if (connected) {
            logConnectedDevices(appContext);
        }
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

    private boolean scanHeadsetAudioDevices() {
        AudioManager audioManager = appContext.getSystemService(AudioManager.class);
        if (audioManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_ALL)) {
                if (isHeadsetAudioType(device.getType())) {
                    return true;
                }
            }
        }
        @SuppressWarnings("deprecation")
        boolean wired = audioManager.isWiredHeadsetOn();
        return wired;
    }

    private static boolean isHeadsetAudioType(int type) {
        if (isUsbAudioType(type)) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES;
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

    public static void logConnectedDevices(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            RtcDebugLog.i(TAG, "audio devices: API<23, skip dump");
            return;
        }
        AudioManager audioManager = context.getApplicationContext().getSystemService(AudioManager.class);
        if (audioManager == null) {
            RtcDebugLog.w(TAG, "audio devices: AudioManager null");
            return;
        }
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        RtcDebugLog.i(TAG, "audio devices count=" + devices.length);
        for (AudioDeviceInfo device : devices) {
            RtcDebugLog.i(TAG, "  device id=" + device.getId()
                    + " type=" + deviceTypeName(device.getType())
                    + " in=" + device.isSource()
                    + " out=" + device.isSink()
                    + " name=" + safeDeviceName(device));
        }
    }

    private static String safeDeviceName(AudioDeviceInfo device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            CharSequence name = device.getProductName();
            return name != null ? name.toString() : "?";
        }
        return "?";
    }

    private static String deviceTypeName(int type) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (type == AudioDeviceInfo.TYPE_USB_HEADSET) return "USB_HEADSET";
            if (type == AudioDeviceInfo.TYPE_USB_DEVICE) return "USB_DEVICE";
        }
        if (type == AudioDeviceInfo.TYPE_USB_ACCESSORY) return "USB_ACCESSORY";
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) return "WIRED_HEADSET";
        if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return "BUILTIN_SPEAKER";
        if (type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) return "BUILTIN_EARPIECE";
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return "BT_SCO";
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return "BT_A2DP";
        return "type_" + type;
    }
}
