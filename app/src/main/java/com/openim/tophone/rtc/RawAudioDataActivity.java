package com.openim.tophone.rtc;



import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.openim.tophone.R;
import com.openim.tophone.ui.main.DomainConfigActivity;
import com.openim.tophone.utils.AppVersionUtil;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DeviceUtils;
import com.ss.bytertc.engine.RTCRoom;
import com.ss.bytertc.engine.RTCRoomConfig;
import com.ss.bytertc.engine.RTCVideo;
import com.ss.bytertc.engine.UserInfo;
import com.ss.bytertc.engine.VideoCanvas;
import com.ss.bytertc.engine.data.StreamIndex;
import com.ss.bytertc.engine.handler.IRTCRoomEventHandler;
import com.ss.bytertc.engine.handler.IRTCVideoEventHandler;
import com.ss.bytertc.engine.type.AnsMode;
import com.ss.bytertc.engine.type.AudioProfileType;
import com.ss.bytertc.engine.type.ChannelProfile;
import com.ss.bytertc.engine.type.ConnectionState;
import com.ss.bytertc.engine.type.MediaTypeEnhancementConfig;
import com.ss.bytertc.engine.type.NetworkQualityStats;
import com.ss.bytertc.engine.type.RTCRoomStats;

import java.text.SimpleDateFormat;
import java.util.Date;

import okhttp3.OkHttpClient;

public class RawAudioDataActivity extends RtcBaseActivity {

    private static final String TAG = "RawAudioDataActivity";
    private static final long REJOIN_DELAY_MS = 2000L;

    private static String token;

    private int clickCount;
    private long lastClickTime;

    private ImageView joinResultIcon;
    private MaterialButton btnJoinRoom;
    private Switch microphoneSwitch;
    private Switch audioRouteSwitch;
    private TextView usernameTextView;
    private TextView roomIDDisplay;
    private TextView roomStatusTextView;
    private TextView onlineUsersCountTextView;
    private ProgressBar loadingIndicator;
    private FrameLayout localViewContainer;

    private UsbAudioDetector usbAudioDetector;
    private BroadcastReceiver phoneCallReceiver;

    private RTCVideo rtcVideo;
    private RTCRoom rtcRoom;

    private boolean isJoined;
    private boolean isLoopJoinRoom;
    private boolean joinInProgress;
    private boolean configReady;
    private boolean preferSpeakerOutput;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private Runnable pendingRejoin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_raw_audio);
        initUI();
        setTitle(getString(R.string.title_raw_audio_data) + " v" + AppVersionUtil.getVersionName(this));
        if (restorePersistedSession()) {
            configReady = true;
            hideLoading();
            refreshUserIdDisplay();
            syncUiForJoinedState();
        } else {
            setupUsbAudioMonitoring();
            updateCheckInStatus();
            refreshUserIdDisplay();
            updateStatusForCurrentState();
            showLoading(getString(R.string.rtc_loading_config));
            refreshRtcAppIdOnStartup();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void initUI() {
        joinResultIcon = findViewById(R.id.join_result_icon);
        btnJoinRoom = findViewById(R.id.btn_join_room);
        microphoneSwitch = findViewById(R.id.audio_mute_switch);
        audioRouteSwitch = findViewById(R.id.audio_route_switch);
        usernameTextView = findViewById(R.id.username);
        roomIDDisplay = findViewById(R.id.room_id_display);
        roomStatusTextView = findViewById(R.id.room_status);
        onlineUsersCountTextView = findViewById(R.id.onlineUsersCount);
        loadingIndicator = findViewById(R.id.loading_indicator);
        localViewContainer = findViewById(R.id.local_view_container);
        setupHiddenEntry(joinResultIcon);
        setupControlListeners();
        updateJoinButtonState();
    }

    private void setupControlListeners() {
        btnJoinRoom.setOnClickListener(v -> {
            if (isJoined) {
                leaveRoom();
                return;
            }
            startManualJoin();
        });

        microphoneSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (rtcVideo == null) {
                return;
            }
            try {
                if (isChecked) {
                    rtcVideo.startAudioCapture();
                } else {
                    rtcVideo.stopAudioCapture();
                }
            } catch (Exception e) {
                Log.e(TAG, "microphone switch failed", e);
            }
        });

        audioRouteSwitch.setOnCheckedChangeListener(this::handleSpeakerRouteSwitch);
    }

    private void handleSpeakerRouteSwitch(CompoundButton buttonView, boolean isChecked) {
        if (rtcVideo == null) {
            RtcToastUtil.showLongToast(this, getString(R.string.rtc_join_room_first_speaker));
            buttonView.setOnCheckedChangeListener(null);
            buttonView.setChecked(false);
            buttonView.setOnCheckedChangeListener(this::handleSpeakerRouteSwitch);
            return;
        }
        if (usbAudioDetector != null && usbAudioDetector.isHeadsetModeActive()) {
            RtcToastUtil.showLongToast(this, getString(R.string.rtc_usb_bridge_active));
            buttonView.setOnCheckedChangeListener(null);
            buttonView.setChecked(false);
            buttonView.setOnCheckedChangeListener(this::handleSpeakerRouteSwitch);
            return;
        }
        preferSpeakerOutput = !isChecked;
        RtcSessionController.getInstance().updatePreferSpeaker(preferSpeakerOutput);
    }

    private void updateJoinButtonState() {
        if (btnJoinRoom == null) {
            return;
        }
        btnJoinRoom.setEnabled(configReady && !joinInProgress);
        if (isJoined) {
            btnJoinRoom.setText(getString(R.string.rtc_leave_room));
            btnJoinRoom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#E91E63")));
        } else {
            btnJoinRoom.setText(getString(R.string.rtc_join_room));
            btnJoinRoom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#4CAF50")));
        }
    }

    private void startManualJoin() {
        if (isJoined || joinInProgress) {
            return;
        }
        if (!isCheckedIn()) {
            RtcToastUtil.showAlert(this, getString(R.string.rtc_status_wait_checkin));
            return;
        }
        String roomID = getAssignedRoomID();
        if (TextUtils.isEmpty(roomID)) {
            RtcToastUtil.showAlert(this, getString(R.string.rtc_no_room_account));
            return;
        }
        isLoopJoinRoom = true;
        showJoinLoading(getString(R.string.rtc_status_connecting));
        refreshRtcAppIdBeforeJoin(roomID);
    }

    private void setupUsbAudioMonitoring() {
        RtcRoomSession session = RtcRoomSession.get();
        UsbAudioDetector existing = session.getUsbAudioDetector();
        if (existing != null) {
            usbAudioDetector = existing;
        } else {
            usbAudioDetector = new UsbAudioDetector(this);
            usbAudioDetector.start();
        }
        usbAudioDetector.setListener(connected -> runOnUiThread(() -> {
            RtcSessionController.getInstance().onUsbAudioChanged(connected);
            updateStatusForCurrentState();
            if (connected) {
                tryAutoJoinRoom();
            }
        }));
    }

    private void updateCheckInStatus() {
        var sp = getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), MODE_PRIVATE);
        String roomID = sp.getString(Constants.getAssignedRoomIDKey(), null);
        if (roomIDDisplay != null) {
            roomIDDisplay.setText(TextUtils.isEmpty(roomID) ? "—" : roomID);
        }
        if (!sp.contains(Constants.getCheckedInKey())) {
            applyJoinResultIcon(R.drawable.icon_warning);
            return;
        }
        boolean checkedIn = sp.getBoolean(Constants.getCheckedInKey(), false);
        if (!checkedIn) {
            isLoopJoinRoom = false;
            if (isJoined) {
                leaveRoom();
            }
            applyJoinResultIcon(R.drawable.icon_warning);
            return;
        }
        if (isJoined) {
            applyJoinResultIcon(R.drawable.icon_success);
        } else if (!joinInProgress) {
            applyJoinResultIcon(R.drawable.icon_taiji);
        }
        if (checkedIn && !isJoined && !joinInProgress && !RtcRoomSession.get().hasActiveSession()) {
            tryAutoJoinRoom();
        }
    }

    private void applyJoinResultIcon(int iconRes) {
        var sp = getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), MODE_PRIVATE);
        if (sp.contains(Constants.getCheckedInKey()) && !sp.getBoolean(Constants.getCheckedInKey(), false)) {
            joinResultIcon.setImageResource(R.drawable.icon_warning);
        } else {
            joinResultIcon.setImageResource(iconRes);
        }
    }

    private boolean isCheckedIn() {
        var sp = getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), MODE_PRIVATE);
        return sp.getBoolean(Constants.getCheckedInKey(), false);
    }

    private String getAssignedRoomID() {
        return getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), MODE_PRIVATE)
                .getString(Constants.getAssignedRoomIDKey(), "");
    }

    private boolean isHeadsetReady() {
        return usbAudioDetector != null && usbAudioDetector.isHeadsetModeActive();
    }

    private boolean isServerConnectionReady() {
        return RtcBackgroundJoiner.isServerConnected();
    }

    /** Auto-join when checked in + server connected, or when USB headset is ready. */
    private boolean canProceedAutoJoin(boolean reconnect) {
        if (reconnect) {
            return true;
        }
        if (isHeadsetReady()) {
            return true;
        }
        return isCheckedIn()
                && isServerConnectionReady()
                && !TextUtils.isEmpty(getAssignedRoomID());
    }

    private void tryAutoJoinRoom() {
        tryAutoJoinRoom(false);
    }

    /** @param reconnect true when re-joining after a drop (headset not required). */
    private void tryAutoJoinRoom(boolean reconnect) {
        if (isJoined || joinInProgress) {
            return;
        }
        if (!isCheckedIn()) {
            updateStatusForCurrentState();
            return;
        }
        String roomID = getAssignedRoomID();
        if (TextUtils.isEmpty(roomID)) {
            updateStatusForCurrentState();
            return;
        }
        if (!canProceedAutoJoin(reconnect)) {
            updateStatusForCurrentState();
            return;
        }
        isLoopJoinRoom = true;
        showJoinLoading(getString(R.string.rtc_status_connecting));
        refreshRtcAppIdBeforeJoin(roomID);
    }

    private void updateStatusForCurrentState() {
        if (joinInProgress) {
            setStatusText(getString(R.string.rtc_status_connecting));
            return;
        }
        if (isJoined) {
            setStatusText(getString(R.string.rtc_status_connected));
            return;
        }
        if (!isCheckedIn()) {
            setStatusText(getString(R.string.rtc_status_wait_checkin));
            return;
        }
        if (TextUtils.isEmpty(getAssignedRoomID())) {
            setStatusText(getString(R.string.rtc_status_wait_checkin));
            return;
        }
        if (!isHeadsetReady() && !canProceedAutoJoin(false)) {
            setStatusText(getString(R.string.rtc_status_wait_headset));
            return;
        }
        setStatusText(getString(R.string.rtc_status_connecting));
    }

    private void setStatusText(String text) {
        if (roomStatusTextView != null) {
            roomStatusTextView.setText(text);
        }
    }

    private void initRTCVideo() {
        if (TextUtils.isEmpty(Constants.RTC_APP_ID)) {
            RtcToastUtil.showLongToast(this, getString(R.string.rtc_app_id_missing));
            return;
        }
        if (rtcVideo != null) {
            if (isJoined) {
                bindRtcSession();
            }
            return;
        }
        rtcVideo = RTCVideo.createRTCVideo(this, Constants.RTC_APP_ID, rtcVideoConnectionHandler, null, null);
        rtcVideo.startAudioCapture();
        setLocalRenderView();
        MediaTypeEnhancementConfig mediaTypeEnhancementConfig = new MediaTypeEnhancementConfig();
        mediaTypeEnhancementConfig.enhanceAudio = true;
        rtcVideo.setCellularEnhancement(mediaTypeEnhancementConfig);
        if (isJoined) {
            bindRtcSession();
        }
    }

    private void bindRtcSession() {
        if (rtcVideo == null) {
            return;
        }
        RtcSessionController controller = RtcSessionController.getInstance();
        boolean headset = usbAudioDetector != null && usbAudioDetector.isHeadsetModeActive();
        controller.setUsbAudioConnected(headset);
        controller.bindSession(rtcVideo, this, preferSpeakerOutput);
    }

    private void onInitialConfigReady() {
        configReady = true;
        hideLoading();
        updateJoinButtonState();
        updateStatusForCurrentState();
        if (!isJoined) {
            tryAutoJoinRoom();
        }
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
        Log.i(TAG, "RTC_APP_ID=" + appId + " (server)");
    }

    private void refreshRtcAppIdOnStartup() {
        RtcConfigLoader.fetchAppId(okHttpClient, new RtcConfigLoader.AppIdCallback() {
            @Override
            public void onSuccess(String appId) {
                mHandler.post(() -> {
                    applyRtcAppId(appId);
                    onInitialConfigReady();
                });
            }

            @Override
            public void onFailure(String message) {
                mHandler.post(() -> {
                    Log.w(TAG, "startup config failed: " + message);
                    configReady = false;
                    hideLoading();
                    updateJoinButtonState();
                    RtcToastUtil.showAlert(RawAudioDataActivity.this,
                            getString(R.string.rtc_config_fetch_failed, message));
                });
            }
        });
    }

    private void refreshRtcAppIdBeforeJoin(String roomID) {
        RtcConfigLoader.fetchAppId(okHttpClient, new RtcConfigLoader.AppIdCallback() {
            @Override
            public void onSuccess(String appId) {
                runOnUiThread(() -> {
                    applyRtcAppId(appId);
                    verifyAndJoinRoom(roomID);
                });
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    joinInProgress = false;
                    hideJoinLoading();
                    setStatusText(getString(R.string.rtc_status_join_failed));
                    RtcToastUtil.showAlert(RawAudioDataActivity.this,
                            getString(R.string.rtc_config_fetch_failed, message));
                    scheduleReconnect();
                });
            }
        });
    }

    private void verifyAndJoinRoom(String roomID) {
        String rtcUserId = getRtcUserId();
        refreshUserIdDisplay();
        RoomVerifier.verifyRoom(roomID, rtcUserId, RawAudioDataActivity.this, new RoomVerifier.RoomCallback() {
            @Override
            public void onResult(boolean isExist, String t) {
                runOnUiThread(() -> {
                    if (!isExist) {
                        joinInProgress = false;
                        hideJoinLoading();
                        setStatusText(getString(R.string.rtc_status_join_failed));
                        RtcToastUtil.showAlert(RawAudioDataActivity.this,
                                getString(R.string.rtc_room_not_found, roomID));
                        scheduleReconnect();
                        return;
                    }
                    if (TextUtils.isEmpty(t) || !RtcTokenUtil.isValidFormat(t)) {
                        joinInProgress = false;
                        hideJoinLoading();
                        setStatusText(getString(R.string.rtc_status_join_failed));
                        RtcToastUtil.showAlert(RawAudioDataActivity.this,
                                getString(R.string.rtc_token_invalid));
                        scheduleReconnect();
                        return;
                    }
                    warnIfTokenAppIdMismatch(t);
                    initRTCVideo();
                    if (rtcVideo == null) {
                        joinInProgress = false;
                        hideJoinLoading();
                        scheduleReconnect();
                        return;
                    }
                    token = t;
                    joinRoom(roomID);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    joinInProgress = false;
                    hideJoinLoading();
                    setStatusText(getString(R.string.rtc_status_join_failed));
                    RtcToastUtil.showAlert(RawAudioDataActivity.this,
                            getString(R.string.rtc_verify_room_failed, e.getMessage()));
                    scheduleReconnect();
                });
            }

            @Override
            public void onMessage(String message) {
                runOnUiThread(() -> {
                    joinInProgress = false;
                    hideJoinLoading();
                    setStatusText(getString(R.string.rtc_status_join_failed));
                    RtcToastUtil.showAlert(RawAudioDataActivity.this, message);
                    scheduleReconnect();
                });
            }
        });
    }

    private void setLocalRenderView() {
        TextureView localTextureView = new TextureView(this);
        localViewContainer.removeAllViews();
        localViewContainer.addView(localTextureView);
        VideoCanvas videoCanvas = new VideoCanvas();
        videoCanvas.renderView = localTextureView;
        videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN;
        rtcVideo.setLocalVideoCanvas(StreamIndex.STREAM_INDEX_MAIN, videoCanvas);
    }

    private void joinRoom(String roomID) {
        if (rtcVideo == null) {
            Log.e(TAG, "joinRoom skipped: rtcVideo null");
            return;
        }
        joinInProgress = true;
        updateJoinButtonState();
        if (rtcRoom != null) {
            rtcRoom.leaveRoom();
            rtcRoom.destroy();
            rtcRoom = null;
        }
        rtcRoom = rtcVideo.createRTCRoom(roomID);
        rtcRoom.setRTCRoomEventHandler(rtcRoomEventHandler);
        rtcVideo.setAudioProfile(AudioProfileType.AUDIO_PROFILE_HD);
        rtcVideo.setAnsMode(AnsMode.ANS_MODE_HIGH);

        refreshUserIdDisplay();
        startRoomKeepLifeService();
        String rtcUserId = getRtcUserId();
        UserInfo userInfo = new UserInfo(rtcUserId, "");
        RTCRoomConfig roomConfig = new RTCRoomConfig(
                ChannelProfile.CHANNEL_PROFILE_CHAT_ROOM,
                true,
                true,
                false
        );
        rtcRoom.joinRoom(token, userInfo, roomConfig);
    }

    private void startRoomKeepLifeService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent serviceIntent = new Intent(RawAudioDataActivity.this, RoomKeepLifeService.class);
            serviceIntent.putExtra("command", "start");
            ContextCompat.startForegroundService(RawAudioDataActivity.this, serviceIntent);
        }
    }

    private void stopRoomKeepLifeService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent serviceIntent = new Intent(RawAudioDataActivity.this, RoomKeepLifeService.class);
            serviceIntent.putExtra("command", "stop");
            ContextCompat.startForegroundService(RawAudioDataActivity.this, serviceIntent);
        }
    }

    private void leaveRoom() {
        isLoopJoinRoom = false;
        cancelPendingRejoin();
        RtcRoomSession.get().clear();
        RtcSessionController.getInstance().clearSession();
        applyJoinResultIcon(R.drawable.icon_failed);
        if (rtcRoom != null) {
            rtcRoom.leaveRoom();
            rtcRoom.destroy();
            rtcRoom = null;
            stopRoomKeepLifeService();
        }
        isJoined = false;
        joinInProgress = false;
        destroyRtcEngine();
        updateStatusForCurrentState();
        updateJoinButtonState();
    }

    private void cancelPendingRejoin() {
        if (pendingRejoin != null) {
            mHandler.removeCallbacks(pendingRejoin);
            pendingRejoin = null;
        }
    }

    private void scheduleReconnect() {
        if (!isLoopJoinRoom || !isCheckedIn()) {
            return;
        }
        String roomID = getAssignedRoomID();
        if (TextUtils.isEmpty(roomID)) {
            return;
        }
        cancelPendingRejoin();
        setStatusText(getString(R.string.rtc_status_reconnecting));
        applyJoinResultIcon(R.drawable.icon_taiji);
        pendingRejoin = () -> {
            pendingRejoin = null;
            if (!isLoopJoinRoom || isJoined || joinInProgress) {
                return;
            }
            if (!isCheckedIn() || TextUtils.isEmpty(getAssignedRoomID())) {
                return;
            }
            tryAutoJoinRoom(true);
        };
        mHandler.postDelayed(pendingRejoin, REJOIN_DELAY_MS);
    }

    private final IRTCVideoEventHandler rtcVideoConnectionHandler = new IRTCVideoEventHandler() {
        @Override
        public void onNetworkTypeChanged(int type) {
            super.onNetworkTypeChanged(type);
            runOnUiThread(() -> {
                if (type == 0) {
                    isJoined = false;
                    applyJoinResultIcon(R.drawable.icon_failed);
                    updateJoinButtonState();
                    scheduleReconnect();
                } else if (isLoopJoinRoom && !isJoined && !joinInProgress) {
                    scheduleReconnect();
                }
            });
        }

        @Override
        public void onConnectionStateChanged(int state, int reason) {
            super.onConnectionStateChanged(state, reason);
            runOnUiThread(() -> {
                if (state == ConnectionState.CONNECTION_STATE_RECONNECTING.getValue()) {
                    setStatusText(getString(R.string.rtc_status_reconnecting));
                } else if (state == ConnectionState.CONNECTION_STATE_CONNECTED.getValue()
                        || state == ConnectionState.CONNECTION_STATE_RECONNECTED.getValue()) {
                    if (isJoined) {
                        setStatusText(getString(R.string.rtc_status_connected));
                    }
                } else if (state == ConnectionState.CONNECTION_STATE_LOST.getValue()
                        || state == ConnectionState.CONNECTION_STATE_FAILED.getValue()
                        || state == ConnectionState.CONNECTION_STATE_DISCONNECTED.getValue()) {
                    isJoined = false;
                    updateJoinButtonState();
                    scheduleReconnect();
                }
            });
        }
    };

    private final IRTCRoomEventHandler rtcRoomEventHandler = new IRTCRoomEventHandler() {
        @Override
        public void onRoomStateChanged(String roomID, String uid, int state, String extraInfo) {
            super.onRoomStateChanged(roomID, uid, state, extraInfo);
            Log.i(TAG, "onRoomStateChanged state=" + state + " uid=" + uid + " extra=" + extraInfo);
            runOnUiThread(() -> handleRoomStateChanged(state));
        }

        @Override
        public void onRoomStats(RTCRoomStats stats) {
            super.onRoomStats(stats);
            runOnUiThread(() -> {
                if (onlineUsersCountTextView == null) {
                    return;
                }
                @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                String formattedDate = sdf.format(new Date());
                onlineUsersCountTextView.setText(getString(R.string.rtc_online_users, stats.users, formattedDate));
            });
        }

        @Override
        public void onNetworkQuality(NetworkQualityStats localQuality, NetworkQualityStats[] remoteQualities) {
            super.onNetworkQuality(localQuality, remoteQualities);
        }
    };

    private void handleRoomStateChanged(int state) {
        if (state != 0) {
            isJoined = false;
            joinInProgress = false;
            hideJoinLoading();
            updateJoinButtonState();
            if (isLoopJoinRoom) {
                setStatusText(getString(R.string.rtc_status_reconnecting));
                scheduleReconnect();
                return;
            }
            leaveRoom();
            return;
        }
        isJoined = true;
        joinInProgress = false;
        isLoopJoinRoom = true;
        hideJoinLoading();
        bindRtcSession();
        syncUiForJoinedState();
    }

    private void syncUiForJoinedState() {
        applyJoinResultIcon(R.drawable.icon_success);
        setStatusText(getString(R.string.rtc_status_connected));
        refreshUserIdDisplay();
        updateJoinButtonState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerPhoneCallReceiver();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterPhoneCallReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (RtcRoomSession.get().hasActiveSession() && rtcVideo == null) {
            restorePersistedSession();
        }
        updateCheckInStatus();
        refreshUserIdDisplay();
        if (isJoined) {
            syncUiForJoinedState();
        } else {
            updateStatusForCurrentState();
        }
        updateJoinButtonState();
        if (isJoined && rtcVideo != null) {
            bindRtcSession();
        } else if (!isJoined && !joinInProgress && !RtcRoomSession.get().hasActiveSession()) {
            tryAutoJoinRoom();
        }
    }

    private void registerPhoneCallReceiver() {
        if (phoneCallReceiver != null) {
            return;
        }
        phoneCallReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!RtcSessionController.ACTION_PHONE_CALL_STATE.equals(intent.getAction())) {
                    return;
                }
                boolean active = intent.getBooleanExtra(RtcSessionController.EXTRA_PHONE_CALL_ACTIVE, false);
                if (active && RtcSessionController.getInstance().isUsbAudioConnected()) {
                    bindRtcSession();
                }
            }
        };
        IntentFilter filter = new IntentFilter(RtcSessionController.ACTION_PHONE_CALL_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(phoneCallReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(phoneCallReceiver, filter);
        }
    }

    private void unregisterPhoneCallReceiver() {
        if (phoneCallReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(phoneCallReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        phoneCallReceiver = null;
    }

    private String getRtcUserId() {
        android.content.SharedPreferences sp = getSharedPreferences(
                Constants.getSharedPrefsKeys_FILE_NAME(), MODE_PRIVATE);
        String username = sp.getString(Constants.getNormalUsernameKey(), "");
        if (!TextUtils.isEmpty(username)) {
            return username;
        }
        String nickname = sp.getString(Constants.getSharedPrefsKeys_NICKNAME(), "NULL");
        if (!TextUtils.isEmpty(nickname) && !"NULL".equals(nickname)) {
            return nickname;
        }
        return DeviceUtils.getAndroidId(this);
    }

    private void refreshUserIdDisplay() {
        if (usernameTextView != null) {
            usernameTextView.setText(getRtcUserId());
        }
    }

    @Override
    protected void onDestroy() {
        cancelPendingRejoin();
        unregisterPhoneCallReceiver();
        if (isJoined && isLoopJoinRoom) {
            persistSessionForBackground();
            super.onDestroy();
            return;
        }
        if (usbAudioDetector != null) {
            usbAudioDetector.stop();
            usbAudioDetector = null;
        }
        RtcSessionController.getInstance().clearSession();
        destroyRtcEngine();
        super.onDestroy();
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
        isLoopJoinRoom = session.isLoopJoin();
        joinInProgress = false;
        setupUsbAudioMonitoring();
        if (rtcRoom != null) {
            rtcRoom.setRTCRoomEventHandler(rtcRoomEventHandler);
        }
        bindRtcSession();
        updateCheckInStatus();
        return true;
    }

    private void persistSessionForBackground() {
        if (usbAudioDetector != null) {
            usbAudioDetector.setListener(null);
        }
        RtcRoomSession.get().persist(rtcVideo, rtcRoom, usbAudioDetector, isJoined, isLoopJoinRoom);
        rtcVideo = null;
        rtcRoom = null;
        usbAudioDetector = null;
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

    private void warnIfTokenAppIdMismatch(String rtcToken) {
        String tokenAppId = RtcTokenUtil.extractAppId(rtcToken);
        if (TextUtils.isEmpty(tokenAppId) || TextUtils.equals(tokenAppId, Constants.RTC_APP_ID)) {
            return;
        }
        Log.w(TAG, "token appId differs: token=" + tokenAppId + " server=" + Constants.RTC_APP_ID);
    }

    private void setupHiddenEntry(View targetView) {
        targetView.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastClickTime > 800) {
                clickCount = 0;
            }
            lastClickTime = now;
            clickCount++;
            if (clickCount >= 5) {
                clickCount = 0;
                startActivity(new Intent(this, DomainConfigActivity.class));
            }
        });
    }

    private void showLoading(String message) {
        setStatusText(message);
        loadingIndicator.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        loadingIndicator.setVisibility(View.GONE);
    }

    private void showJoinLoading(String message) {
        joinInProgress = true;
        showLoading(message);
        updateJoinButtonState();
    }

    private void hideJoinLoading() {
        joinInProgress = false;
        hideLoading();
        updateStatusForCurrentState();
        updateJoinButtonState();
    }
}
