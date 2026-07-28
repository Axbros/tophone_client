package com.openim.tophone.telecom;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.text.TextUtils;
import android.util.Log;
import android.telecom.VideoProfile;

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

    public static final String ACTION_CALL_STATE_CHANGED =
            "com.openim.tophone.action.CALL_STATE_CHANGED";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_NUMBER = "number";
    public static final String EXTRA_INCOMING = "incoming";
    public static final String EXTRA_CONNECTED_AT = "connectedAt";

    private static volatile ToPhoneInCallService instance;
    private static volatile Call currentCall;
    private static volatile boolean currentIncoming;
    private static volatile long currentConnectedAt;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Call, Call.Callback> callbacks = new ConcurrentHashMap<>();
    private final Set<Call> activeReported =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        currentCall = call;
        currentIncoming = call.getState() == Call.STATE_RINGING;
        currentConnectedAt = 0L;

        Call.Callback callback = new Call.Callback() {
            @Override
            public void onStateChanged(Call changedCall, int state) {
                handleCallState(changedCall, state);
            }

            @Override
            public void onDetailsChanged(Call changedCall, Call.Details details) {
                publishCallState(changedCall, changedCall.getState());
            }
        };
        callbacks.put(call, callback);
        call.registerCallback(callback);
        handleCallState(call, call.getState());
        launchCallScreen(call, call.getState());
    }

    @Override
    public void onCallRemoved(Call call) {
        publishCallState(call, Call.STATE_DISCONNECTED);
        Call.Callback callback = callbacks.remove(call);
        if (callback != null) {
            call.unregisterCallback(callback);
        }
        activeReported.remove(call);
        if (currentCall == call) {
            currentCall = null;
            currentIncoming = false;
            currentConnectedAt = 0L;
        }
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
        if (instance == this) {
            instance = null;
            currentCall = null;
            currentIncoming = false;
            currentConnectedAt = 0L;
        }
        super.onDestroy();
    }

    private void handleCallState(Call call, int state) {
        currentCall = call;
        if (state == Call.STATE_RINGING) {
            currentIncoming = true;
        }
        if (state == Call.STATE_ACTIVE && currentConnectedAt == 0L) {
            currentConnectedAt = System.currentTimeMillis();
        }
        publishCallState(call, state);
        launchCallScreen(call, state);

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

    private void launchCallScreen(Call call, int state) {
        if (state == Call.STATE_DISCONNECTED) {
            return;
        }
        Intent intent = new Intent(this, InCallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        putCallState(intent, call, state);
        try {
            startActivity(intent);
        } catch (RuntimeException error) {
            Log.e(TAG, "unable to open in-call screen", error);
        }
    }

    private void publishCallState(Call call, int state) {
        Intent intent = new Intent(ACTION_CALL_STATE_CHANGED);
        intent.setPackage(getPackageName());
        putCallState(intent, call, state);
        sendBroadcast(intent);
    }

    private void putCallState(Intent intent, Call call, int state) {
        intent.putExtra(EXTRA_STATE, state);
        intent.putExtra(EXTRA_NUMBER, resolveNumber(call));
        intent.putExtra(EXTRA_INCOMING, currentIncoming);
        intent.putExtra(EXTRA_CONNECTED_AT, currentConnectedAt);
    }

    public static int getCurrentState() {
        Call call = currentCall;
        return call == null ? Call.STATE_DISCONNECTED : call.getState();
    }

    public static String getCurrentNumber() {
        ToPhoneInCallService service = instance;
        Call call = currentCall;
        return service == null || call == null ? "" : service.resolveNumber(call);
    }

    public static boolean isCurrentIncoming() {
        return currentIncoming;
    }

    public static long getCurrentConnectedAt() {
        return currentConnectedAt;
    }

    public static boolean isMuted() {
        ToPhoneInCallService service = instance;
        CallAudioState state = service == null ? null : service.getCallAudioState();
        return state != null && state.isMuted();
    }

    public static boolean isSpeakerOn() {
        ToPhoneInCallService service = instance;
        CallAudioState state = service == null ? null : service.getCallAudioState();
        return state != null && state.getRoute() == CallAudioState.ROUTE_SPEAKER;
    }

    public static void answerCurrentCall() {
        Call call = currentCall;
        if (call != null && call.getState() == Call.STATE_RINGING) {
            call.answer(VideoProfile.STATE_AUDIO_ONLY);
        }
    }

    public static void declineCurrentCall() {
        Call call = currentCall;
        if (call == null) {
            return;
        }
        if (call.getState() == Call.STATE_RINGING) {
            call.reject(false, null);
        } else {
            call.disconnect();
        }
    }

    public static void disconnectCurrentCall() {
        Call call = currentCall;
        if (call != null) {
            call.disconnect();
        }
    }

    public static boolean toggleMute() {
        ToPhoneInCallService service = instance;
        if (service == null) {
            return false;
        }
        boolean muted = !isMuted();
        service.setMuted(muted);
        return muted;
    }

    public static boolean toggleSpeaker() {
        ToPhoneInCallService service = instance;
        CallAudioState state = service == null ? null : service.getCallAudioState();
        if (service == null || state == null) {
            return false;
        }
        boolean enableSpeaker = state.getRoute() != CallAudioState.ROUTE_SPEAKER;
        int targetRoute = CallAudioState.ROUTE_SPEAKER;
        if (!enableSpeaker) {
            int supportedRoutes = state.getSupportedRouteMask();
            targetRoute = (supportedRoutes & CallAudioState.ROUTE_EARPIECE) != 0
                    ? CallAudioState.ROUTE_EARPIECE
                    : CallAudioState.ROUTE_WIRED_HEADSET;
        }
        service.setAudioRoute(targetRoute);
        return enableSpeaker;
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
