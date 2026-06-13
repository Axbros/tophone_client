package com.openim.tophone.rtc;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

import androidx.annotation.Nullable;

import com.ss.bytertc.engine.RTCVideo;
import com.ss.bytertc.engine.data.AudioRoute;
import com.ss.bytertc.engine.type.AudioScenarioType;

/**
 * Routes VolcEngine RTC audio to USB sound card when present (hardware bridge mode),
 * otherwise falls back to speaker or wired earpiece.
 */
public final class RtcAudioRouter {

    private static final String TAG = "RtcAudioRouter";

    @Nullable
    private static AudioFocusRequest audioFocusRequest;

    private RtcAudioRouter() {
    }

    public static AudioRoute applyPreferredRoute(RTCVideo rtcVideo, Context context,
                                                boolean preferSpeaker, boolean usbAudioConnected) {
        RtcDebugLog.i(TAG, "applyPreferredRoute preferSpeaker=" + preferSpeaker
                + " usbAudioConnected=" + usbAudioConnected);
        if (rtcVideo == null) {
            RtcDebugLog.w(TAG, "rtcVideo is null, skip routing");
            return AudioRoute.AUDIO_ROUTE_SPEAKERPHONE;
        }
        int scenarioResult = rtcVideo.setAudioScenario(AudioScenarioType.AUDIO_SCENARIO_COMMUNICATION);
        RtcDebugLog.i(TAG, "setAudioScenario(COMMUNICATION) result=" + scenarioResult);

        if (usbAudioConnected) {
            UsbAudioDetector.logConnectedDevices(context);
            AudioRoute route = setRouteWithFallback(rtcVideo,
                    AudioRoute.AUDIO_ROUTE_HEADSET_USB,
                    AudioRoute.AUDIO_ROUTE_HEADSET,
                    AudioRoute.AUDIO_ROUTE_EARPIECE);
            RtcDebugLog.i(TAG, "USB bridge route applied: " + route);
            return route;
        }

        if (preferSpeaker) {
            int code = rtcVideo.setAudioRoute(AudioRoute.AUDIO_ROUTE_SPEAKERPHONE);
            RtcDebugLog.i(TAG, "setAudioRoute(SPEAKERPHONE) result=" + code);
            return AudioRoute.AUDIO_ROUTE_SPEAKERPHONE;
        }

        AudioRoute route = setRouteWithFallback(rtcVideo,
                AudioRoute.AUDIO_ROUTE_HEADSET,
                AudioRoute.AUDIO_ROUTE_EARPIECE,
                AudioRoute.AUDIO_ROUTE_SPEAKERPHONE);
        RtcDebugLog.i(TAG, "Headset route applied: " + route);
        return route;
    }

    public static void retainCommunicationAudioFocus(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            RtcDebugLog.w(TAG, "retainAudioFocus: AudioManager null");
            return;
        }
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        int focusResult;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attributes)
                        .setAcceptsDelayedFocusGain(true)
                        .build();
            }
            focusResult = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            focusResult = audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
        RtcDebugLog.i(TAG, "retainAudioFocus mode=IN_COMMUNICATION result=" + focusResult
                + " (1=GRANTED)");
    }

    public static void releaseCommunicationAudioFocus(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        audioManager.setMode(AudioManager.MODE_NORMAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(null);
        }
        RtcDebugLog.i(TAG, "releaseAudioFocus mode=NORMAL");
    }

    private static AudioRoute setRouteWithFallback(RTCVideo rtcVideo, AudioRoute... routes) {
        for (AudioRoute route : routes) {
            int code = rtcVideo.setAudioRoute(route);
            RtcDebugLog.i(TAG, "setAudioRoute(" + route + ") result=" + code + " (0=ok)");
            if (code == 0) {
                return route;
            }
        }
        int fallbackCode = rtcVideo.setAudioRoute(AudioRoute.AUDIO_ROUTE_SPEAKERPHONE);
        RtcDebugLog.w(TAG, "all routes failed, fallback SPEAKERPHONE result=" + fallbackCode);
        return AudioRoute.AUDIO_ROUTE_SPEAKERPHONE;
    }
}
