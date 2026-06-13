package com.openim.tophone.rtc;

import android.content.Context;

import androidx.annotation.Nullable;

import com.ss.bytertc.engine.RTCVideo;
import com.ss.bytertc.engine.data.AudioRoute;

import java.lang.ref.WeakReference;

/**
 * Keeps RTC engine reachable while a room session is active so phone-call events
 * can re-apply USB audio routing without leaving the room.
 */
public final class RtcSessionController {

    public static final String ACTION_PHONE_CALL_STATE = "com.openim.tophone.PHONE_CALL_STATE";
    public static final String EXTRA_PHONE_CALL_ACTIVE = "phone_call_active";

    private static final RtcSessionController INSTANCE = new RtcSessionController();

    private WeakReference<RTCVideo> rtcVideoRef = new WeakReference<>(null);
    private WeakReference<Context> contextRef = new WeakReference<>(null);
    private boolean preferSpeaker;
    private boolean phoneCallActive;
    private boolean sessionActive;
    private boolean usbAudioConnected;

    private RtcSessionController() {
    }

    public static RtcSessionController getInstance() {
        return INSTANCE;
    }

    public void bindSession(RTCVideo rtcVideo, Context context, boolean preferSpeaker) {
        rtcVideoRef = new WeakReference<>(rtcVideo);
        contextRef = new WeakReference<>(context.getApplicationContext());
        this.preferSpeaker = preferSpeaker;
        sessionActive = true;
        RtcDebugLog.i("RtcSession", "bindSession preferSpeaker=" + preferSpeaker
                + " usb=" + usbAudioConnected);
        applyPreferredRoute();
    }

    public void setUsbAudioConnected(boolean connected) {
        usbAudioConnected = connected;
    }

    public void updatePreferSpeaker(boolean preferSpeaker) {
        this.preferSpeaker = preferSpeaker;
        applyPreferredRoute();
    }

    public void onUsbAudioChanged(boolean connected) {
        usbAudioConnected = connected;
        RtcDebugLog.i("RtcSession", "onUsbAudioChanged connected=" + connected);
        applyPreferredRoute();
    }

    public void onPhoneCallStateChanged(boolean active) {
        phoneCallActive = active;
        RtcDebugLog.i("RtcSession", "onPhoneCallStateChanged active=" + active
                + " usb=" + usbAudioConnected + " session=" + sessionActive);
        Context context = contextRef.get();
        RTCVideo rtcVideo = rtcVideoRef.get();
        if (!sessionActive || context == null || rtcVideo == null) {
            return;
        }
        if (active && usbAudioConnected) {
            RtcAudioRouter.retainCommunicationAudioFocus(context);
            rtcVideo.startAudioCapture();
            applyPreferredRoute();
        } else if (!active) {
            RtcAudioRouter.releaseCommunicationAudioFocus(context);
            applyPreferredRoute();
        }
    }

    public void clearSession() {
        Context context = contextRef.get();
        if (phoneCallActive && context != null) {
            RtcAudioRouter.releaseCommunicationAudioFocus(context);
        }
        rtcVideoRef = new WeakReference<>(null);
        contextRef = new WeakReference<>(null);
        sessionActive = false;
        phoneCallActive = false;
    }

    public boolean isPhoneCallActive() {
        return phoneCallActive;
    }

    public boolean isSessionActive() {
        return sessionActive;
    }

    @Nullable
    public AudioRoute applyPreferredRoute() {
        RTCVideo rtcVideo = rtcVideoRef.get();
        Context context = contextRef.get();
        if (!sessionActive || rtcVideo == null || context == null) {
            return null;
        }
        return RtcAudioRouter.applyPreferredRoute(rtcVideo, context, preferSpeaker, usbAudioConnected);
    }

    public boolean isUsbAudioConnected() {
        return usbAudioConnected;
    }
}
