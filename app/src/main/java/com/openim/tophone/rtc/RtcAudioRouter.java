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

    private static final AudioManager.OnAudioFocusChangeListener AUDIO_FOCUS_LISTENER =
            focusChange -> RtcDebugLog.i(TAG, "audioFocusChange=" + focusChange);

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
        int scenarioResult = rtcVideo.setAudioScenario(AudioScenarioType.AUDIO_SCENARIO_HIGHQUALITY_COMMUNICATION);
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
        try {
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
                            .setOnAudioFocusChangeListener(AUDIO_FOCUS_LISTENER)
                            .build();
                }
                focusResult = audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                focusResult = audioManager.requestAudioFocus(AUDIO_FOCUS_LISTENER,
                        AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
            }
            RtcDebugLog.i(TAG, "retainAudioFocus mode=IN_COMMUNICATION result=" + focusResult
                    + " (1=GRANTED)");
        } catch (Exception e) {
            RtcDebugLog.e(TAG, "retainAudioFocus failed: " + e.getMessage());
        }
    }

    public static void releaseCommunicationAudioFocus(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        try {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(AUDIO_FOCUS_LISTENER);
            }
            RtcDebugLog.i(TAG, "releaseAudioFocus mode=NORMAL");
        } catch (Exception e) {
            RtcDebugLog.e(TAG, "releaseAudioFocus failed: " + e.getMessage());
        }
    }

    /**
     * Maximize both Android streams used by RTC communication routes. Voice-call
     * covers communication/earpiece/speaker routing, while music covers vendor
     * speaker implementations and most USB audio devices.
     */
    public static void maximizeRtcOutputVolume(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            RtcDebugLog.w(TAG, "maximizeRtcOutputVolume: AudioManager null");
            return;
        }
        if (audioManager.isVolumeFixed()) {
            RtcDebugLog.i(TAG, "maximizeRtcOutputVolume: device volume is fixed");
            return;
        }
        maximizeStream(audioManager, AudioManager.STREAM_VOICE_CALL, "VOICE_CALL");
        maximizeStream(audioManager, AudioManager.STREAM_MUSIC, "MUSIC");
    }

    private static void maximizeStream(AudioManager audioManager, int streamType, String streamName) {
        try {
            int before = audioManager.getStreamVolume(streamType);
            int maximum = audioManager.getStreamMaxVolume(streamType);
            if (maximum > 0 && before != maximum) {
                audioManager.setStreamVolume(streamType, maximum, 0);
            }
            RtcDebugLog.i(TAG, "maximizeVolume stream=" + streamName
                    + " before=" + before + " max=" + maximum
                    + " after=" + audioManager.getStreamVolume(streamType));
        } catch (RuntimeException error) {
            RtcDebugLog.e(TAG, "maximizeVolume failed stream=" + streamName
                    + " error=" + error.getMessage());
        }
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
