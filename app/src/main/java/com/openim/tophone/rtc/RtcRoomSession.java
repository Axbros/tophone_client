package com.openim.tophone.rtc;

import androidx.annotation.Nullable;

import com.ss.bytertc.engine.RTCRoom;
import com.ss.bytertc.engine.RTCVideo;

/**
 * Keeps an active RTC room session alive when {@link RawAudioDataActivity} is closed
 * (e.g. user returns to the home screen).
 */
final class RtcRoomSession {

    private static RtcRoomSession instance;

    @Nullable
    private RTCVideo rtcVideo;
    @Nullable
    private RTCRoom rtcRoom;
    @Nullable
    private UsbAudioDetector usbAudioDetector;
    private boolean joined;
    private boolean loopJoin;

    static synchronized RtcRoomSession get() {
        if (instance == null) {
            instance = new RtcRoomSession();
        }
        return instance;
    }

    boolean hasActiveSession() {
        return joined && rtcVideo != null;
    }

    boolean isJoined() {
        return joined;
    }

    boolean isLoopJoin() {
        return loopJoin;
    }

    @Nullable
    RTCVideo getRtcVideo() {
        return rtcVideo;
    }

    @Nullable
    RTCRoom getRtcRoom() {
        return rtcRoom;
    }

    @Nullable
    UsbAudioDetector getUsbAudioDetector() {
        return usbAudioDetector;
    }

    void persist(@Nullable RTCVideo video,
                 @Nullable RTCRoom room,
                 @Nullable UsbAudioDetector detector,
                 boolean joined,
                 boolean loopJoin) {
        this.rtcVideo = video;
        this.rtcRoom = room;
        this.usbAudioDetector = detector;
        this.joined = joined;
        this.loopJoin = loopJoin;
    }

    void clear() {
        rtcVideo = null;
        rtcRoom = null;
        if (usbAudioDetector != null) {
            usbAudioDetector.stop();
            usbAudioDetector = null;
        }
        joined = false;
        loopJoin = false;
    }
}
