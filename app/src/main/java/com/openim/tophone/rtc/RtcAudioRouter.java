package com.openim.tophone.rtc;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

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
        if (rtcVideo == null) {
            return AudioRoute.AUDIO_ROUTE_SPEAKERPHONE;
        }
        rtcVideo.setAudioScenario(AudioScenarioType.AUDIO_SCENARIO_COMMUNICATION);

        if (usbAudioConnected) {
            AudioRoute route = setRouteWithFallback(rtcVideo,
                    AudioRoute.AUDIO_ROUTE_HEADSET_USB,
                    AudioRoute.AUDIO_ROUTE_HEADSET,
                    AudioRoute.AUDIO_ROUTE_EARPIECE);
            Log.i(TAG, "USB bridge route applied: " + route);
            return route;
        }

        if (preferSpeaker) {
            rtcVideo.setAudioRoute(AudioRoute.AUDIO_ROUTE_SPEAKERPHONE);
            Log.i(TAG, "Speaker route applied");
            return AudioRoute.AUDIO_ROUTE_SPEAKERPHONE;
        }

        AudioRoute route = setRouteWithFallback(rtcVideo,
                AudioRoute.AUDIO_ROUTE_HEADSET,
                AudioRoute.AUDIO_ROUTE_EARPIECE,
                AudioRoute.AUDIO_ROUTE_SPEAKERPHONE);
        Log.i(TAG, "Headset route applied: " + route);
        return route;
    }

    public static void retainCommunicationAudioFocus(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
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
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
        }
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
    }

    private static AudioRoute setRouteWithFallback(RTCVideo rtcVideo, AudioRoute... routes) {
        for (AudioRoute route : routes) {
            if (rtcVideo.setAudioRoute(route) == 0) {
                return route;
            }
        }
        rtcVideo.setAudioRoute(AudioRoute.AUDIO_ROUTE_SPEAKERPHONE);
        return AudioRoute.AUDIO_ROUTE_SPEAKERPHONE;
    }
}
