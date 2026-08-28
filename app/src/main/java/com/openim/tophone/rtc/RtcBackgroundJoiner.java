package com.openim.tophone.rtc;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.openim.tophone.mqtt.MqttManager;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DeviceUtils;
import com.ss.bytertc.engine.RTCRoom;
import com.ss.bytertc.engine.RTCRoomConfig;
import com.ss.bytertc.engine.RTCVideo;
import com.ss.bytertc.engine.UserInfo;
import com.ss.bytertc.engine.handler.IRTCRoomEventHandler;
import com.ss.bytertc.engine.handler.IRTCVideoEventHandler;
import com.ss.bytertc.engine.type.AnsMode;
import com.ss.bytertc.engine.type.AudioProfileType;
import com.ss.bytertc.engine.type.ChannelProfile;
import com.ss.bytertc.engine.type.ConnectionState;
import com.ss.bytertc.engine.type.MediaTypeEnhancementConfig;

import okhttp3.OkHttpClient;

/**
 * Joins the assigned RTC room in the background so the home screen can stay visible.
 */
public final class RtcBackgroundJoiner {

    public interface Listener {
        void onRoomStateChanged(boolean joined, boolean joining);
    }

    private static final String TAG = "RtcBackgroundJoiner";
    private static final long REJOIN_DELAY_MS = 2000L;
    private static final long WATCHDOG_INTERVAL_MS = 5000L;
    private static final long JOIN_ATTEMPT_TIMEOUT_MS = 15000L;

    private static RtcBackgroundJoiner instance;

    private Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient okHttpClient = new OkHttpClient();

    private UsbAudioDetector usbAudioDetector;
    private RTCVideo rtcVideo;
    private RTCRoom rtcRoom;

    private boolean configReady;
    private boolean isJoined;
    private boolean joinInProgress;
    private boolean userWantsRoom;
    private boolean watchdogScheduled;
    private long joinAttemptStartedAt;
    private long joinGeneration;
    private long activeJoinGeneration;
    private String activeRoomID = "";
    private String token;
    private Runnable pendingRejoin;
    private Listener listener;

    public static boolean isServerConnected() {
        if (!Constants.isUseMqtt()) {
            return true;
        }
        return MqttManager.getInstance().isConnected();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        notifyListener();
    }

    public boolean isJoining() {
        return joinInProgress;
    }

    public static synchronized RtcBackgroundJoiner get() {
        if (instance == null) {
            instance = new RtcBackgroundJoiner();
        }
        return instance;
    }

    public void init(Context context) {
        appContext = context.getApplicationContext();
        ensureUsbMonitoring();
        if (RtcRoomSession.get().hasActiveSession()) {
            restorePersistedSession();
        } else {
            refreshRtcAppIdOnStartup();
        }
        if (isCheckedIn() && !TextUtils.isEmpty(getAssignedRoomID())) {
            userWantsRoom = true;
            startWatchdog();
        }
    }

    public void requestJoin() {
        if (appContext == null) {
            return;
        }
        userWantsRoom = true;
        startWatchdog();
        notifyListener();
        attemptJoin();
    }

    /** Auto-join after check-in + room id and/or MQTT connected. */
    public void tryJoinWhenReady() {
        if (appContext == null || !isCheckedIn() || TextUtils.isEmpty(getAssignedRoomID())) {
            return;
        }
        userWantsRoom = true;
        startWatchdog();
        notifyListener();
        attemptJoin();
    }

    private void attemptJoin() {
        if (RtcRoomSession.get().hasActiveSession() || isJoined) {
            return;
        }
        if (joinInProgress) {
            if (!isJoinAttemptStalled()) {
                return;
            }
            Log.w(TAG, "RTC join attempt timed out, resetting before retry");
            resetJoinAttempt();
        }
        if (!canJoin()) {
            return;
        }
        Log.i(TAG, "attemptJoin");
        joinInProgress = true;
        joinAttemptStartedAt = System.currentTimeMillis();
        activeJoinGeneration = ++joinGeneration;
        notifyListener();
        refreshRtcAppIdBeforeJoin(getAssignedRoomID(), activeJoinGeneration);
    }

    public boolean shouldSwitchBeOn() {
        return userWantsRoom || isJoined() || joinInProgress;
    }

    public void leaveRoom() {
        userWantsRoom = false;
        stopWatchdog();
        cancelPendingRejoin();
        activeJoinGeneration = ++joinGeneration;
        joinAttemptStartedAt = 0L;
        activeRoomID = "";
        RtcRoomSession.get().clear();
        RtcSessionController.getInstance().clearSession();
        if (rtcRoom != null) {
            rtcRoom.leaveRoom();
            rtcRoom.destroy();
            rtcRoom = null;
            stopRoomKeepLifeService();
        }
        isJoined = false;
        joinInProgress = false;
        destroyRtcEngine();
        notifyListener();
    }

    public boolean isJoined() {
        return isJoined || RtcRoomSession.get().hasActiveSession();
    }

    private boolean canJoin() {
        return isCheckedIn()
                && !TextUtils.isEmpty(getAssignedRoomID())
                && (isHeadsetReady() || isServerConnected());
    }

    private void notifyListener() {
        if (listener == null) {
            return;
        }
        mainHandler.post(() -> listener.onRoomStateChanged(isJoined(), joinInProgress));
    }

    private void ensureUsbMonitoring() {
        if (usbAudioDetector != null) {
            return;
        }
        RtcRoomSession session = RtcRoomSession.get();
        if (session.getUsbAudioDetector() != null) {
            usbAudioDetector = session.getUsbAudioDetector();
        } else {
            usbAudioDetector = new UsbAudioDetector(appContext);
            usbAudioDetector.start();
        }
        usbAudioDetector.setListener(connected -> mainHandler.post(() -> {
            RtcSessionController.getInstance().onUsbAudioChanged(connected);
            if (userWantsRoom) {
                attemptJoin();
            }
        }));
    }

    private void refreshRtcAppIdOnStartup() {
        RtcConfigLoader.fetchAppId(okHttpClient, new RtcConfigLoader.AppIdCallback() {
            @Override
            public void onSuccess(String appId) {
                mainHandler.post(() -> {
                    applyRtcAppId(appId);
                    configReady = true;
                });
            }

            @Override
            public void onFailure(String message) {
                mainHandler.post(() -> {
                    Log.w(TAG, "startup config failed: " + message);
                    configReady = false;
                });
            }
        });
    }

    private void refreshRtcAppIdBeforeJoin(String roomID, long generation) {
        RtcConfigLoader.fetchAppId(okHttpClient, new RtcConfigLoader.AppIdCallback() {
            @Override
            public void onSuccess(String appId) {
                mainHandler.post(() -> {
                    if (!isCurrentJoinAttempt(generation)) {
                        return;
                    }
                    applyRtcAppId(appId);
                    configReady = true;
                    verifyAndJoinRoom(roomID, generation);
                });
            }

            @Override
            public void onFailure(String message) {
                mainHandler.post(() -> {
                    if (!isCurrentJoinAttempt(generation)) {
                        return;
                    }
                    joinInProgress = false;
                    joinAttemptStartedAt = 0L;
                    notifyListener();
                    scheduleReconnect();
                });
            }
        });
    }

    private void verifyAndJoinRoom(String roomID, long generation) {
        joinInProgress = true;
        notifyListener();
        String rtcUserId = getRtcUserId();
        RoomVerifier.verifyRoom(roomID, rtcUserId, appContext, new RoomVerifier.RoomCallback() {
            @Override
            public void onResult(boolean isExist, String t, String appID) {
                mainHandler.post(() -> {
                    if (!isCurrentJoinAttempt(generation)) {
                        return;
                    }
                    if (!isExist) {
                        joinInProgress = false;
                        joinAttemptStartedAt = 0L;
                        notifyListener();
                        scheduleReconnect();
                        return;
                    }
                    if (TextUtils.isEmpty(t) || !RtcTokenUtil.isValidFormat(t)) {
                        joinInProgress = false;
                        joinAttemptStartedAt = 0L;
                        notifyListener();
                        scheduleReconnect();
                        return;
                    }
                    if (TextUtils.isEmpty(appID)) {
                        joinInProgress = false;
                        joinAttemptStartedAt = 0L;
                        notifyListener();
                        scheduleReconnect();
                        return;
                    }
                    // 使用服务端签发本次 Token 时读取到的同一套 AppID，不使用旧内存值。
                    applyRtcAppId(appID);
                    initRtcVideo();
                    if (rtcVideo == null) {
                        joinInProgress = false;
                        joinAttemptStartedAt = 0L;
                        scheduleReconnect();
                        return;
                    }
                    token = t;
                    joinRoom(roomID, generation);
                });
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
                    if (!isCurrentJoinAttempt(generation)) {
                        return;
                    }
                    joinInProgress = false;
                    joinAttemptStartedAt = 0L;
                    notifyListener();
                    scheduleReconnect();
                });
            }

            @Override
            public void onMessage(String message) {
                mainHandler.post(() -> {
                    if (!isCurrentJoinAttempt(generation)) {
                        return;
                    }
                    joinInProgress = false;
                    joinAttemptStartedAt = 0L;
                    notifyListener();
                    scheduleReconnect();
                });
            }
        });
    }

    private void initRtcVideo() {
        if (TextUtils.isEmpty(Constants.RTC_APP_ID)) {
            Log.e(TAG, "RTC_APP_ID missing");
            return;
        }
        if (rtcVideo != null) {
            bindRtcSession();
            return;
        }
        rtcVideo = RTCVideo.createRTCVideo(appContext, Constants.RTC_APP_ID, rtcVideoConnectionHandler, null, null);
        rtcVideo.startAudioCapture();
        MediaTypeEnhancementConfig mediaTypeEnhancementConfig = new MediaTypeEnhancementConfig();
        mediaTypeEnhancementConfig.enhanceAudio = true;
        rtcVideo.setCellularEnhancement(mediaTypeEnhancementConfig);
        bindRtcSession();
    }

    private void bindRtcSession() {
        if (rtcVideo == null || appContext == null) {
            return;
        }
        boolean headset = usbAudioDetector != null && usbAudioDetector.isHeadsetModeActive();
        RtcSessionController controller = RtcSessionController.getInstance();
        controller.setUsbAudioConnected(headset);
        controller.bindSession(rtcVideo, appContext, false);
    }

    private void joinRoom(String roomID, long generation) {
        if (rtcVideo == null) {
            return;
        }
        if (!isCurrentJoinAttempt(generation)) {
            return;
        }
        joinInProgress = true;
        if (rtcRoom != null) {
            rtcRoom.leaveRoom();
            rtcRoom.destroy();
            rtcRoom = null;
        }
        activeRoomID = roomID;
        rtcRoom = rtcVideo.createRTCRoom(roomID);
        rtcRoom.setRTCRoomEventHandler(rtcRoomEventHandler);
        rtcVideo.setAudioProfile(AudioProfileType.AUDIO_PROFILE_HD);
        rtcVideo.setAnsMode(AnsMode.ANS_MODE_HIGH);
        startRoomKeepLifeService();
        UserInfo userInfo = new UserInfo(getRtcUserId(), "");
        RTCRoomConfig roomConfig = new RTCRoomConfig(
                ChannelProfile.CHANNEL_PROFILE_CHAT_ROOM,
                true,
                true,
                false
        );
        rtcRoom.joinRoom(token, userInfo, roomConfig);
    }

    private void handleRoomStateChanged(String roomID, int state) {
        if (TextUtils.isEmpty(activeRoomID)
                || (!TextUtils.isEmpty(roomID) && !activeRoomID.equals(roomID))) {
            Log.w(TAG, "ignoring stale RTC room callback room=" + roomID
                    + " activeRoom=" + activeRoomID);
            return;
        }
        if (state != 0) {
            boolean shouldRejoin = userWantsRoom;
            resetJoinAttempt();
            isJoined = false;
            joinInProgress = false;
            if (shouldRejoin) {
                scheduleReconnect();
            } else {
                leaveRoom();
            }
            return;
        }
        isJoined = true;
        joinInProgress = false;
        joinAttemptStartedAt = 0L;
        activeJoinGeneration = 0L;
        bindRtcSession();
        RtcAudioRouter.maximizeRtcOutputVolume(appContext);
        persistSession();
        notifyListener();
    }

    private void persistSession() {
        if (usbAudioDetector != null) {
            usbAudioDetector.setListener(null);
        }
        RtcRoomSession.get().persist(rtcVideo, rtcRoom, usbAudioDetector, isJoined, userWantsRoom);
        rtcVideo = null;
        rtcRoom = null;
        usbAudioDetector = null;
    }

    private boolean restorePersistedSession() {
        RtcRoomSession session = RtcRoomSession.get();
        if (!session.hasActiveSession()) {
            return false;
        }
        rtcVideo = session.getRtcVideo();
        rtcRoom = session.getRtcRoom();
        activeRoomID = getAssignedRoomID();
        usbAudioDetector = session.getUsbAudioDetector();
        isJoined = session.isJoined();
        userWantsRoom = session.isLoopJoin();
        joinInProgress = false;
        joinAttemptStartedAt = 0L;
        activeJoinGeneration = 0L;
        configReady = true;
        ensureUsbMonitoring();
        if (rtcRoom != null) {
            rtcRoom.setRTCRoomEventHandler(rtcRoomEventHandler);
        }
        bindRtcSession();
        if (isJoined) {
            RtcAudioRouter.maximizeRtcOutputVolume(appContext);
        }
        notifyListener();
        return true;
    }

    private void scheduleReconnect() {
        if (!userWantsRoom || !isCheckedIn()) {
            notifyListener();
            return;
        }
        if (TextUtils.isEmpty(getAssignedRoomID())) {
            notifyListener();
            return;
        }
        cancelPendingRejoin();
        pendingRejoin = () -> {
            pendingRejoin = null;
            if (!userWantsRoom || isJoined) {
                return;
            }
            if (!isCheckedIn() || TextUtils.isEmpty(getAssignedRoomID())) {
                return;
            }
            if (!RtcRoomSession.get().hasActiveSession()) {
                attemptJoin();
            }
        };
        mainHandler.postDelayed(pendingRejoin, REJOIN_DELAY_MS);
    }

    private void startWatchdog() {
        if (watchdogScheduled || appContext == null) {
            return;
        }
        watchdogScheduled = true;
        mainHandler.post(watchdogRunnable);
    }

    private void stopWatchdog() {
        watchdogScheduled = false;
        mainHandler.removeCallbacks(watchdogRunnable);
    }

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!watchdogScheduled) {
                return;
            }
            if (!userWantsRoom || !isCheckedIn() || TextUtils.isEmpty(getAssignedRoomID())) {
                stopWatchdog();
                notifyListener();
                return;
            }

            if (joinInProgress && isJoinAttemptStalled()) {
                Log.w(TAG, "RTC watchdog detected a stalled join attempt");
                resetJoinAttempt();
            }
            if (!isJoined() && !RtcRoomSession.get().hasActiveSession()) {
                attemptJoin();
            }
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    private boolean isJoinAttemptStalled() {
        return joinInProgress
                && joinAttemptStartedAt > 0L
                && System.currentTimeMillis() - joinAttemptStartedAt >= JOIN_ATTEMPT_TIMEOUT_MS;
    }

    private boolean isCurrentJoinAttempt(long generation) {
        return joinInProgress && activeJoinGeneration == generation;
    }

    private void resetJoinAttempt() {
        activeJoinGeneration = ++joinGeneration;
        joinInProgress = false;
        isJoined = false;
        joinAttemptStartedAt = 0L;
        token = null;
        activeRoomID = "";

        RtcRoomSession session = RtcRoomSession.get();
        RTCRoom currentRoom = rtcRoom != null ? rtcRoom : session.getRtcRoom();
        RTCVideo currentVideo = rtcVideo != null ? rtcVideo : session.getRtcVideo();
        UsbAudioDetector persistedDetector = session.getUsbAudioDetector();
        RtcSessionController.getInstance().clearSession();
        if (currentRoom != null) {
            currentRoom.leaveRoom();
            currentRoom.destroy();
        }
        if (currentVideo != null) {
            currentVideo.stopAudioCapture();
            RTCVideo.destroyRTCVideo();
        }
        session.clear();
        if (persistedDetector != null && usbAudioDetector == persistedDetector) {
            usbAudioDetector = null;
        }
        rtcRoom = null;
        rtcVideo = null;
        if (currentRoom != null || currentVideo != null) {
            stopRoomKeepLifeService();
        }
        ensureUsbMonitoring();
        notifyListener();
    }

    private void cancelPendingRejoin() {
        if (pendingRejoin != null) {
            mainHandler.removeCallbacks(pendingRejoin);
            pendingRejoin = null;
        }
    }

    private void destroyRtcEngine() {
        RtcRoomSession.get().clear();
        if (rtcVideo != null) {
            rtcVideo.stopAudioCapture();
        }
        if (rtcRoom != null) {
            rtcRoom.destroy();
            rtcRoom = null;
        }
        rtcVideo = null;
        RTCVideo.destroyRTCVideo();
    }

    private void applyRtcAppId(String appId) {
        if (TextUtils.isEmpty(appId)) {
            return;
        }
        appId = appId.trim();
        boolean changed = !TextUtils.isEmpty(Constants.RTC_APP_ID)
                && !TextUtils.equals(Constants.RTC_APP_ID, appId);
        if (changed && rtcVideo != null && !isJoined) {
            destroyRtcEngine();
        }
        Constants.RTC_APP_ID = appId;
    }

    private boolean isCheckedIn() {
        if (appContext == null) {
            return false;
        }
        return appContext.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .getBoolean(Constants.getCheckedInKey(), false);
    }

    private String getAssignedRoomID() {
        if (appContext == null) {
            return "";
        }
        return appContext.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .getString(Constants.getAssignedRoomIDKey(), "");
    }

    private String getRtcUserId() {
        if (appContext == null) {
            return "";
        }
        var sp = appContext.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE);
        String username = sp.getString(Constants.getNormalUsernameKey(), "");
        if (!TextUtils.isEmpty(username)) {
            return username;
        }
        String nickname = sp.getString(Constants.getSharedPrefsKeys_NICKNAME(), "NULL");
        if (!TextUtils.isEmpty(nickname) && !"NULL".equals(nickname)) {
            return nickname;
        }
        return DeviceUtils.getAndroidId(appContext);
    }

    private boolean isHeadsetReady() {
        return usbAudioDetector != null && usbAudioDetector.isHeadsetModeActive();
    }

    private void startRoomKeepLifeService() {
        if (appContext == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        Intent serviceIntent = new Intent(appContext, RoomKeepLifeService.class);
        serviceIntent.putExtra("command", "start");
        ContextCompat.startForegroundService(appContext, serviceIntent);
    }

    private void stopRoomKeepLifeService() {
        if (appContext == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        Intent serviceIntent = new Intent(appContext, RoomKeepLifeService.class);
        serviceIntent.putExtra("command", "stop");
        ContextCompat.startForegroundService(appContext, serviceIntent);
    }

    private final IRTCVideoEventHandler rtcVideoConnectionHandler = new IRTCVideoEventHandler() {
        @Override
        public void onConnectionStateChanged(int state, int reason) {
            mainHandler.post(() -> {
                if (state == ConnectionState.CONNECTION_STATE_LOST.getValue()
                        || state == ConnectionState.CONNECTION_STATE_FAILED.getValue()
                        || state == ConnectionState.CONNECTION_STATE_DISCONNECTED.getValue()) {
                    boolean shouldRejoin = userWantsRoom;
                    resetJoinAttempt();
                    if (shouldRejoin) {
                        scheduleReconnect();
                    } else {
                        leaveRoom();
                    }
                }
            });
        }
    };

    private final IRTCRoomEventHandler rtcRoomEventHandler = new IRTCRoomEventHandler() {
        @Override
        public void onRoomStateChanged(String roomID, String uid, int state, String extraInfo) {
            Log.i(TAG, "onRoomStateChanged state=" + state + " uid=" + uid);
            mainHandler.post(() -> handleRoomStateChanged(roomID, state));
        }
    };
}
