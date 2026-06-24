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

    private static RtcBackgroundJoiner instance;

    private Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private RtcCacheUtil cacheUtil;

    private UsbAudioDetector usbAudioDetector;
    private RTCVideo rtcVideo;
    private RTCRoom rtcRoom;

    private boolean configReady;
    private boolean isJoined;
    private boolean joinInProgress;
    private boolean userWantsRoom;
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
        cacheUtil = new RtcCacheUtil(appContext);
        ensureUsbMonitoring();
        if (RtcRoomSession.get().hasActiveSession()) {
            restorePersistedSession();
        } else if (!configReady) {
            refreshRtcAppIdOnStartup();
        }
    }

    public void requestJoin() {
        if (appContext == null) {
            return;
        }
        userWantsRoom = true;
        notifyListener();
        attemptJoin();
    }

    /** Auto-join after check-in + room id and/or MQTT connected. */
    public void tryJoinWhenReady() {
        if (appContext == null || !isCheckedIn() || TextUtils.isEmpty(getAssignedRoomId())) {
            return;
        }
        userWantsRoom = true;
        notifyListener();
        attemptJoin();
    }

    private void attemptJoin() {
        if (RtcRoomSession.get().hasActiveSession() || isJoined || joinInProgress) {
            return;
        }
        if (!canJoin()) {
            return;
        }
        Log.i(TAG, "attemptJoin");
        joinInProgress = true;
        notifyListener();
        refreshRtcAppIdBeforeJoin(getAssignedRoomId());
    }

    public boolean shouldSwitchBeOn() {
        return userWantsRoom || isJoined() || joinInProgress;
    }

    public void leaveRoom() {
        userWantsRoom = false;
        cancelPendingRejoin();
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
                && !TextUtils.isEmpty(getAssignedRoomId())
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
                    applyRtcAppId(appId, false);
                    configReady = true;
                });
            }

            @Override
            public void onFailure(String message) {
                mainHandler.post(() -> {
                    Log.w(TAG, "startup config failed: " + message);
                    String cached = cacheUtil.getKeyAppId();
                    if (!TextUtils.isEmpty(cached)) {
                        applyRtcAppId(cached, true);
                    }
                    configReady = !TextUtils.isEmpty(cached);
                });
            }
        });
    }

    private void refreshRtcAppIdBeforeJoin(String roomId) {
        RtcConfigLoader.fetchAppId(okHttpClient, new RtcConfigLoader.AppIdCallback() {
            @Override
            public void onSuccess(String appId) {
                mainHandler.post(() -> {
                    applyRtcAppId(appId, false);
                    configReady = true;
                    verifyAndJoinRoom(roomId);
                });
            }

            @Override
            public void onFailure(String message) {
                mainHandler.post(() -> {
                    joinInProgress = false;
                    userWantsRoom = false;
                    notifyListener();
                    scheduleReconnect();
                });
            }
        });
    }

    private void verifyAndJoinRoom(String roomId) {
        joinInProgress = true;
        notifyListener();
        String rtcUserId = getRtcUserId();
        RoomVerifier.verifyRoom(roomId, rtcUserId, appContext, new RoomVerifier.RoomCallback() {
            @Override
            public void onResult(boolean isExist, String t) {
                mainHandler.post(() -> {
                    if (!isExist) {
                        joinInProgress = false;
                        notifyListener();
                        scheduleReconnect();
                        return;
                    }
                    if (TextUtils.isEmpty(t) || !RtcTokenUtil.isValidFormat(t)) {
                        joinInProgress = false;
                        notifyListener();
                        scheduleReconnect();
                        return;
                    }
                    initRtcVideo();
                    if (rtcVideo == null) {
                        joinInProgress = false;
                        scheduleReconnect();
                        return;
                    }
                    token = t;
                    joinRoom(roomId);
                });
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
                    joinInProgress = false;
                    notifyListener();
                    scheduleReconnect();
                });
            }

            @Override
            public void onMessage(String message) {
                mainHandler.post(() -> {
                    joinInProgress = false;
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

    private void joinRoom(String roomId) {
        if (rtcVideo == null) {
            return;
        }
        joinInProgress = true;
        if (rtcRoom != null) {
            rtcRoom.leaveRoom();
            rtcRoom.destroy();
            rtcRoom = null;
        }
        rtcRoom = rtcVideo.createRTCRoom(roomId);
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

    private void handleRoomStateChanged(int state) {
        if (state != 0) {
            isJoined = false;
            joinInProgress = false;
            if (userWantsRoom) {
                scheduleReconnect();
            } else {
                leaveRoom();
            }
            return;
        }
        isJoined = true;
        joinInProgress = false;
        bindRtcSession();
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
        usbAudioDetector = session.getUsbAudioDetector();
        isJoined = session.isJoined();
        userWantsRoom = session.isLoopJoin();
        joinInProgress = false;
        configReady = true;
        ensureUsbMonitoring();
        if (rtcRoom != null) {
            rtcRoom.setRTCRoomEventHandler(rtcRoomEventHandler);
        }
        bindRtcSession();
        notifyListener();
        return true;
    }

    private void scheduleReconnect() {
        if (!userWantsRoom || !isCheckedIn()) {
            notifyListener();
            return;
        }
        if (TextUtils.isEmpty(getAssignedRoomId())) {
            notifyListener();
            return;
        }
        cancelPendingRejoin();
        pendingRejoin = () -> {
            pendingRejoin = null;
            if (!userWantsRoom || isJoined) {
                return;
            }
            if (!isCheckedIn() || TextUtils.isEmpty(getAssignedRoomId())) {
                return;
            }
            if (!RtcRoomSession.get().hasActiveSession()) {
                refreshRtcAppIdBeforeJoin(getAssignedRoomId());
            }
        };
        mainHandler.postDelayed(pendingRejoin, REJOIN_DELAY_MS);
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

    private void applyRtcAppId(String appId, boolean fromCacheFallback) {
        if (TextUtils.isEmpty(appId)) {
            return;
        }
        Constants.RTC_APP_ID = appId;
        if (!fromCacheFallback) {
            cacheUtil.saveAppID(appId);
        }
    }

    private boolean isCheckedIn() {
        if (appContext == null) {
            return false;
        }
        return appContext.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .getBoolean(Constants.getCheckedInKey(), false);
    }

    private String getAssignedRoomId() {
        if (appContext == null) {
            return "";
        }
        return appContext.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .getString(Constants.getAssignedRoomIdKey(), "");
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
                    isJoined = false;
                    notifyListener();
                    scheduleReconnect();
                }
            });
        }
    };

    private final IRTCRoomEventHandler rtcRoomEventHandler = new IRTCRoomEventHandler() {
        @Override
        public void onRoomStateChanged(String roomId, String uid, int state, String extraInfo) {
            Log.i(TAG, "onRoomStateChanged state=" + state + " uid=" + uid);
            mainHandler.post(() -> handleRoomStateChanged(state));
        }
    };
}
