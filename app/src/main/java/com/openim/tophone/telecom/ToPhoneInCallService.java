package com.openim.tophone.telecom;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.InCallService;
import android.text.TextUtils;
import android.util.Log;

import com.openim.tophone.enums.ActionEnums;
import com.openim.tophone.mqtt.MqttManager;
import com.openim.tophone.utils.MqttEventUtil;
import com.openim.tophone.utils.PhoneStateService;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receives exact Telecom call state after this app is selected as the default dialer.
 */
public class ToPhoneInCallService extends InCallService {
    private static final String TAG = "ToPhoneInCallService";
    private static final long MQTT_RETRY_DELAY_MS = 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Call, Call.Callback> callbacks = new ConcurrentHashMap<>();
    private final Set<Call> activeReported =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        Call.Callback callback = new Call.Callback() {
            @Override
            public void onStateChanged(Call changedCall, int state) {
                handleCallState(changedCall, state);
            }
        };
        callbacks.put(call, callback);
        call.registerCallback(callback);
        handleCallState(call, call.getState());
    }

    @Override
    public void onCallRemoved(Call call) {
        Call.Callback callback = callbacks.remove(call);
        if (callback != null) {
            call.unregisterCallback(callback);
        }
        activeReported.remove(call);
        super.onCallRemoved(call);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (Map.Entry<Call, Call.Callback> entry : callbacks.entrySet()) {
            entry.getKey().unregisterCallback(entry.getValue());
        }
        callbacks.clear();
        activeReported.clear();
        super.onDestroy();
    }

    private void handleCallState(Call call, int state) {
        if (state != Call.STATE_ACTIVE || activeReported.contains(call)) {
            return;
        }
        if (!MqttManager.getInstance().isConnected()) {
            handler.postDelayed(() -> {
                if (callbacks.containsKey(call)) {
                    handleCallState(call, call.getState());
                }
            }, MQTT_RETRY_DELAY_MS);
            return;
        }
        if (!activeReported.add(call)) {
            return;
        }

        String mobile = resolveNumber(call);
        MqttEventUtil.publishEvent(
                ActionEnums.CALL_ACTIVE.getType(),
                mobile,
                "connected"
        );
        Log.i(TAG, "call active reported mobile=" + mobile);
    }

    private String resolveNumber(Call call) {
        Call.Details details = call.getDetails();
        if (details != null) {
            Uri handle = details.getHandle();
            if (handle != null && !TextUtils.isEmpty(handle.getSchemeSpecificPart())) {
                return Uri.decode(handle.getSchemeSpecificPart()).trim();
            }
        }
        String pendingOutgoing = PhoneStateService.getPendingOutgoingNumber();
        return pendingOutgoing == null ? "" : pendingOutgoing.trim();
    }
}
