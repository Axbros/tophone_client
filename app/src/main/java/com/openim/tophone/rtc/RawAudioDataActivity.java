package com.openim.tophone.rtc;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.openim.tophone.R;
import com.openim.tophone.ui.main.DomainConfigActivity;
import com.openim.tophone.utils.AppVersionUtil;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DeviceUtils;
import com.ss.bytertc.engine.IAudioFrameObserver;
import com.ss.bytertc.engine.RTCRoom;
import com.ss.bytertc.engine.RTCRoomConfig;
import com.ss.bytertc.engine.RTCVideo;
import com.ss.bytertc.engine.UserInfo;
import com.ss.bytertc.engine.VideoCanvas;
import com.ss.bytertc.engine.data.AudioChannel;
import com.ss.bytertc.engine.data.AudioFormat;
import com.ss.bytertc.engine.data.AudioFrameCallbackMethod;
import com.ss.bytertc.engine.data.AudioRoute;
import com.ss.bytertc.engine.data.AudioSampleRate;
import com.ss.bytertc.engine.data.RemoteStreamKey;
import com.ss.bytertc.engine.data.StreamIndex;
import com.ss.bytertc.engine.handler.IRTCRoomEventHandler;
import com.ss.bytertc.engine.handler.IRTCVideoEventHandler;
import com.ss.bytertc.engine.type.ChannelProfile;
import com.ss.bytertc.engine.type.ConnectionState;
import com.ss.bytertc.engine.type.MediaTypeEnhancementConfig;
import com.ss.bytertc.engine.type.NetworkQualityStats;
import com.ss.bytertc.engine.type.RTCRoomStats;
import com.ss.bytertc.engine.utils.IAudioFrame;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;

public class RawAudioDataActivity extends RtcBaseActivity {

    private int clickCount = 0;
    private long lastClickTime = 0;
    private static String token;

    private static final String TAG = "RawAudioDataActivity";
    private Button btnJoinRoom;
    private Button btnClearCache;
    private EditText roomIdInput;
    private FrameLayout localViewContainer;
    private FloatWindowManager floatWindowManager;
    private ImageView joinResultIcon;
    private Switch audioFrameCallbackSwitch;
    private Switch microphoneSwitch;
    private Switch audioRouteSwitch;
    private TextView usernameTextView;
    private TextView onlineUsersCountTextView;
    private TextView networkQuality;
    private TextView usbAudioStatus;
    private boolean isLoopJoinRoom;

    private UsbAudioDetector usbAudioDetector;
    private BroadcastReceiver phoneCallReceiver;
    private boolean preferSpeakerOutput;

    Map<AudioRoute, String> audioTypeMap = new HashMap<>();

    RTCVideo rtcVideo;
    RTCRoom rtcRoom;

    boolean isJoined;
    boolean isShowRecordDataLog;
    boolean isShowMixDataLog;
    boolean isShowPlaybackDataLog;
    boolean isShowRemoteUserDataLog;
    TextureView textureView;
    private Button btnOpenFloatWindow;
    private int onlineUsers = 0;
    private static final int REQUEST_CODE_FLOATING_WINDOW = 1001;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private RtcCacheUtil cacheUtil;
    private ProgressBar loadingIndicator;
    private TextView joinLoadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_raw_audio);
        initUI();
        setupUsbAudioMonitoring();
        showLoading(getString(R.string.rtc_loading_config));
        cacheUtil = new RtcCacheUtil(this);
        refreshRtcAppIdOnStartup();
        setTitle(getString(R.string.title_raw_audio_data) + " v" + AppVersionUtil.getVersionName(this));
        setupHiddenEntry(findViewById(R.id.join_result_icon));
        updateCheckInStatus();
    }

    private void updateCheckInStatus() {
        var sp = getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), MODE_PRIVATE);
        String roomId = sp.getString(Constants.getAssignedRoomIdKey(), null);
        if (!TextUtils.isEmpty(roomId)) {
            roomIdInput.setText(roomId);
        }
        lockRoomInput();

        if (!sp.contains(Constants.getCheckedInKey())) {
            return;
        }
        boolean checkedIn = sp.getBoolean(Constants.getCheckedInKey(), false);
        if (!checkedIn) {
            applyJoinResultIcon(R.drawable.icon_warning);
            return;
        }
        if (!isJoined) {
            applyJoinResultIcon(R.drawable.icon_taiji);
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

    private void lockRoomInput() {
        roomIdInput.setEnabled(false);
        roomIdInput.setFocusable(false);
        roomIdInput.setFocusableInTouchMode(false);
        roomIdInput.setClickable(false);
    }

    private void initRTCVideo() {
        if (TextUtils.isEmpty(Constants.RTC_APP_ID)) {
            RtcToastUtil.showLongToast(this, getString(R.string.rtc_app_id_missing));
            return;
        }

        if (rtcVideo != null) {
            RTCVideo.destroyRTCVideo();
            rtcVideo = null;
        }
        rtcVideo = RTCVideo.createRTCVideo(this, Constants.RTC_APP_ID, rtcVideoEventHandler, null, null);

        rtcVideo.startAudioCapture();
        setLocalRenderView();
        rtcVideo.registerAudioFrameObserver(audioFrameObserver);
        rtcVideo.setRtcVideoEventHandler(irtcVideoEventHandler);

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
        boolean usbConnected = usbAudioDetector != null && usbAudioDetector.isUsbAudioConnected();
        controller.setUsbAudioConnected(usbConnected);
        controller.bindSession(rtcVideo, this, preferSpeakerOutput);
        updateUsbAudioStatus(usbConnected);
    }

    private void setupUsbAudioMonitoring() {
        usbAudioDetector = new UsbAudioDetector(this);
        usbAudioDetector.setListener(connected -> runOnUiThread(() -> {
            RtcSessionController.getInstance().onUsbAudioChanged(connected);
            updateUsbAudioStatus(connected);
            if (connected && isJoined) {
                RtcToastUtil.showLongToast(this, getString(R.string.rtc_usb_bridge_active));
            }
        }));
        usbAudioDetector.start();
        updateUsbAudioStatus(usbAudioDetector.isUsbAudioConnected());
    }

    private void updateUsbAudioStatus(boolean connected) {
        if (usbAudioStatus == null) {
            return;
        }
        usbAudioStatus.setText(connected
                ? getString(R.string.rtc_usb_audio_connected)
                : getString(R.string.rtc_usb_audio_disconnected));
    }

    private void onInitialConfigReady(boolean fromCacheFallback) {
        hideLoading();
        access();
        if (fromCacheFallback) {
            RtcToastUtil.showLongToast(this, getString(R.string.rtc_using_cached_config));
        } else if (!TextUtils.isEmpty(Constants.RTC_APP_ID)) {
            RtcToastUtil.showLongToast(this, getString(R.string.rtc_config_loaded));
        }
    }

    private void applyRtcAppId(String appId, boolean fromCacheFallback) {
        if (TextUtils.isEmpty(appId)) {
            return;
        }
        Constants.RTC_APP_ID = appId;
        if (!fromCacheFallback) {
            cacheUtil.saveAppID(appId);
        }
        Log.i(TAG, "RTC_APP_ID=" + appId + (fromCacheFallback ? " (cache fallback)" : " (server)"));
    }

    private void refreshRtcAppIdOnStartup() {
        RtcConfigLoader.fetchAppId(okHttpClient, new RtcConfigLoader.AppIdCallback() {
            @Override
            public void onSuccess(String appId) {
                mHandler.post(() -> {
                    applyRtcAppId(appId, false);
                    onInitialConfigReady(false);
                });
            }

            @Override
            public void onFailure(String message) {
                mHandler.post(() -> {
                    Log.w(TAG, "startup tophone_world failed: " + message);
                    String cached = cacheUtil.getKeyAppId();
                    if (!TextUtils.isEmpty(cached)) {
                        applyRtcAppId(cached, true);
                        onInitialConfigReady(true);
                    } else {
                        onInitialConfigReady(false);
                    }
                });
            }
        });
    }

    private void refreshRtcAppIdBeforeJoin(String roomId) {
        RtcConfigLoader.fetchAppId(okHttpClient, new RtcConfigLoader.AppIdCallback() {
            @Override
            public void onSuccess(String appId) {
                runOnUiThread(() -> {
                    applyRtcAppId(appId, false);
                    verifyAndJoinRoom(roomId);
                });
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    hideJoinLoading();
                    RtcToastUtil.showAlert(RawAudioDataActivity.this,
                            getString(R.string.rtc_config_fetch_failed, message));
                });
            }
        });
    }

    private void verifyAndJoinRoom(String roomId) {
        RoomVerifier.verifyRoom(roomId, getNickname(), RawAudioDataActivity.this, new RoomVerifier.RoomCallback() {
            @Override
            public void onResult(boolean isExist, String t) {
                runOnUiThread(() -> {
                    hideJoinLoading();
                    if (!isExist) {
                        RtcToastUtil.showAlert(RawAudioDataActivity.this,
                                getString(R.string.rtc_room_not_found, roomId));
                        return;
                    }
                    if (TextUtils.isEmpty(t)) {
                        RtcToastUtil.showAlert(RawAudioDataActivity.this,
                                getString(R.string.rtc_token_empty));
                        return;
                    }
                    if (!RtcTokenUtil.isValidFormat(t)) {
                        RtcToastUtil.showAlert(RawAudioDataActivity.this,
                                getString(R.string.rtc_token_invalid));
                        return;
                    }
                    warnIfTokenAppIdMismatch(t);

                    initRTCVideo();
                    if (rtcVideo == null) {
                        return;
                    }
                    btnClearCache.setVisibility(View.GONE);
                    token = t;
                    joinRoom(roomId);
                    audioFrameCallbackSwitch.setEnabled(true);
                    btnJoinRoom.setText(getString(R.string.rtc_leave_room));
                    btnJoinRoom.setBackgroundColor(Color.parseColor("#E91E63"));
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    hideJoinLoading();
                    RtcToastUtil.showAlert(RawAudioDataActivity.this,
                            getString(R.string.rtc_verify_room_failed, e.getMessage()));
                });
            }

            @Override
            public void onMessage(String message) {
                runOnUiThread(() -> {
                    hideJoinLoading();
                    RtcToastUtil.showAlert(RawAudioDataActivity.this, message);
                });
            }
        });
    }

    private void initUI() {
        btnJoinRoom = findViewById(R.id.btn_join_room);
        btnJoinRoom.setEnabled(false);
        loadingIndicator = findViewById(R.id.loading_indicator);
        joinLoadingText = findViewById(R.id.join_loading_text);
        btnClearCache = findViewById(R.id.btn_clear_cache);
        btnClearCache.setEnabled(false);

        roomIdInput = findViewById(R.id.room_id_input);
        lockRoomInput();
        audioFrameCallbackSwitch = findViewById(R.id.audio_callback_switch);
        microphoneSwitch = findViewById(R.id.audio_mute_switch);
        joinResultIcon = findViewById(R.id.join_result_icon);
        audioRouteSwitch = findViewById(R.id.audio_route_switch);
        usernameTextView = findViewById(R.id.username);
        refreshNicknameDisplay();
        btnOpenFloatWindow = findViewById(R.id.btn_float_window);
        onlineUsersCountTextView = findViewById(R.id.onlineUsersCount);
        localViewContainer = findViewById(R.id.local_view_container);
        networkQuality = findViewById(R.id.networkQuality);
        usbAudioStatus = findViewById(R.id.usb_audio_status);
        textureView = new TextureView(this);
        floatWindowManager = new FloatWindowManager(this, textureView);
        floatWindowManager.getCloseButton().setOnClickListener(v -> closeFloatingWindow());
        audioTypeMap.put(AudioRoute.AUDIO_ROUTE_EARPIECE, getString(R.string.rtc_audio_earpiece));
        audioTypeMap.put(AudioRoute.AUDIO_ROUTE_SPEAKERPHONE, getString(R.string.rtc_audio_speaker));
        audioTypeMap.put(AudioRoute.AUDIO_ROUTE_HEADSET_BLUETOOTH, getString(R.string.rtc_audio_bluetooth));
        audioTypeMap.put(AudioRoute.AUDIO_ROUTE_HEADSET_USB, getString(R.string.rtc_audio_usb));
        audioTypeMap.put(AudioRoute.AUDIO_ROUTE_HEADSET, getString(R.string.rtc_audio_wired));
        btnOpenFloatWindow.setOnClickListener(v -> requestFloatingWindowPermission());
        btnJoinRoom.setOnClickListener(v -> {
            String roomId = roomIdInput.getText().toString().trim();
            if (isJoined) {
                isLoopJoinRoom = false;
                leaveRoom();
                btnJoinRoom.setBackgroundColor(Color.parseColor("#4CAF50"));
                return;
            }
            if (TextUtils.isEmpty(roomId)) {
                RtcToastUtil.showAlert(RawAudioDataActivity.this, getString(R.string.rtc_no_room_account));
                return;
            }
            showJoinLoading();
            refreshRtcAppIdBeforeJoin(roomId);
        });

        btnClearCache.setOnClickListener(v -> {
            cacheUtil.clearAllCache();
            RtcToastUtil.showLongToast(RawAudioDataActivity.this, getString(R.string.rtc_cache_cleared));
        });
        audioFrameCallbackSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> enableAudioFrameCallback(isChecked));

        microphoneSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                Log.d("MicrophoneSwitch", "状态切换: " + isChecked);
                if (rtcVideo == null) {
                    return;
                }
                if (isChecked) {
                    rtcVideo.startAudioCapture();
                } else {
                    rtcVideo.stopAudioCapture();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        audioRouteSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (rtcVideo == null) {
                RtcToastUtil.showLongToast(this, getString(R.string.rtc_join_room_first_speaker));
                audioRouteSwitch.setChecked(!isChecked);
                return;
            }
            if (usbAudioDetector != null && usbAudioDetector.isUsbAudioConnected()) {
                RtcToastUtil.showLongToast(this, getString(R.string.rtc_usb_bridge_active));
                audioRouteSwitch.setChecked(false);
                return;
            }
            preferSpeakerOutput = !isChecked;
            RtcSessionController.getInstance().updatePreferSpeaker(preferSpeakerOutput);
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

    private void enableAudioFrameCallback(boolean enable) {
        if (rtcVideo == null) {
            return;
        }
        if (enable) {
            AudioFormat format = new AudioFormat(AudioSampleRate.AUDIO_SAMPLE_RATE_48000, AudioChannel.AUDIO_CHANNEL_MONO);
            rtcVideo.enableAudioFrameCallback(AudioFrameCallbackMethod.AUDIO_FRAME_CALLBACK_RECORD, format);
            rtcVideo.enableAudioFrameCallback(AudioFrameCallbackMethod.AUDIO_FRAME_CALLBACK_MIXED, format);
            rtcVideo.enableAudioFrameCallback(AudioFrameCallbackMethod.AUDIO_FRAME_CALLBACK_PLAYBACK, format);

            AudioFormat audioFormat = new AudioFormat(AudioSampleRate.AUDIO_SAMPLE_RATE_AUTO, AudioChannel.AUDIO_CHANNEL_AUTO);
            rtcVideo.enableAudioFrameCallback(AudioFrameCallbackMethod.AUDIO_FRAME_CALLBACK_REMOTE_USER, audioFormat);
        } else {
            rtcVideo.disableAudioFrameCallback(AudioFrameCallbackMethod.AUDIO_FRAME_CALLBACK_RECORD);
            rtcVideo.disableAudioFrameCallback(AudioFrameCallbackMethod.AUDIO_FRAME_CALLBACK_MIXED);
            rtcVideo.disableAudioFrameCallback(AudioFrameCallbackMethod.AUDIO_FRAME_CALLBACK_PLAYBACK);
            rtcVideo.disableAudioFrameCallback(AudioFrameCallbackMethod.AUDIO_FRAME_CALLBACK_REMOTE_USER);
        }
        isShowPlaybackDataLog = false;
        isShowMixDataLog = false;
        isShowRecordDataLog = false;
        isShowRemoteUserDataLog = false;
    }

    private final IAudioFrameObserver audioFrameObserver = new IAudioFrameObserver() {
        @Override
        public void onRecordAudioFrame(IAudioFrame audioFrame) {
            Log.i(TAG, "onRecordAudioFrame:");
            if (!isShowRecordDataLog) {
                RtcToastUtil.showShortToast(RawAudioDataActivity.this, "onRecordAudioFrame");
                isShowRecordDataLog = true;
            }
        }

        @Override
        public void onPlaybackAudioFrame(IAudioFrame audioFrame) {
            Log.i(TAG, "onPlaybackAudioFrame:");
            if (!isShowPlaybackDataLog) {
                RtcToastUtil.showShortToast(RawAudioDataActivity.this, "onPlaybackAudioFrame");
                isShowPlaybackDataLog = true;
            }
        }

        @Override
        public void onRemoteUserAudioFrame(RemoteStreamKey streamKey, IAudioFrame audioFrame) {
            Log.i(TAG, "onRemoteUserAudioFrame:");
            if (!isShowRemoteUserDataLog) {
                RtcToastUtil.showShortToast(RawAudioDataActivity.this, "onRemoteUserAudioFrame");
                isShowRemoteUserDataLog = true;
            }
        }

        @Override
        public void onMixedAudioFrame(IAudioFrame audioFrame) {
            Log.i(TAG, "onMixedAudioFrame:");
            if (!isShowMixDataLog) {
                RtcToastUtil.showShortToast(RawAudioDataActivity.this, "onMixedAudioFrame");
                isShowMixDataLog = true;
            }
        }
    };

    private final IRTCVideoEventHandler irtcVideoEventHandler = new IRTCVideoEventHandler() {
        @Override
        public void onNetworkTypeChanged(int type) {
            super.onNetworkTypeChanged(type);
            if (type == 0) {
                RtcToastUtil.showAlert(RawAudioDataActivity.this, getString(R.string.rtc_network_disconnected));
                isJoined = false;
                applyJoinResultIcon(R.drawable.icon_failed);
                joinRoom(roomIdInput.getText().toString());
            } else {
                isJoined = true;
                applyJoinResultIcon(R.drawable.icon_success);
            }
        }

        @Override
        public void onConnectionStateChanged(int state, int reason) {
            super.onConnectionStateChanged(state, reason);
            if (state == ConnectionState.CONNECTION_STATE_DISCONNECTED.getValue()) {
                RtcToastUtil.showShortToast(RawAudioDataActivity.this, getString(R.string.rtc_conn_disconnected_12s));
            }
            if (state == ConnectionState.CONNECTION_STATE_CONNECTING.getValue()) {
                RtcToastUtil.showShortToast(RawAudioDataActivity.this, getString(R.string.rtc_conn_connecting));
            }
            if (state == ConnectionState.CONNECTION_STATE_CONNECTED.getValue()) {
                RtcToastUtil.showShortToast(RawAudioDataActivity.this, getString(R.string.rtc_conn_connected));
            }
            if (state == ConnectionState.CONNECTION_STATE_RECONNECTING.getValue()) {
                RtcToastUtil.showShortToast(RawAudioDataActivity.this, getString(R.string.rtc_conn_reconnecting));
            }
            if (state == ConnectionState.CONNECTION_STATE_RECONNECTED.getValue()) {
                RtcToastUtil.showAlert(RawAudioDataActivity.this, getString(R.string.rtc_conn_reconnected));
            }
            if (state == ConnectionState.CONNECTION_STATE_LOST.getValue()) {
                RtcToastUtil.showShortToast(RawAudioDataActivity.this, getString(R.string.rtc_conn_lost));
            }
            if (state == ConnectionState.CONNECTION_STATE_FAILED.getValue()) {
                RtcToastUtil.showShortToast(RawAudioDataActivity.this, getString(R.string.rtc_conn_failed));
            }
        }
    };

    private Runnable createJoinRoomRunnable(final String roomId) {
        return () -> {
            if (rtcRoom == null || rtcVideo == null) {
                Log.e(TAG, "joinRoom skipped: rtcRoom or rtcVideo is null");
                return;
            }
            long currentTime = System.currentTimeMillis();
            Log.d("JoinRoomTimer", "执行joinRoom，roomId=" + roomId +
                    "，时间戳=" + currentTime +
                    "，当前时间=" + new SimpleDateFormat("HH:mm:ss").format(new Date(currentTime)));

            UserInfo userInfo = new UserInfo(getNickname(), "");
            RTCRoomConfig roomConfig = new RTCRoomConfig(
                    ChannelProfile.CHANNEL_PROFILE_CHAT_ROOM,
                    true,
                    true,
                    false
            );
            rtcRoom.joinRoom(token, userInfo, roomConfig);
        };
    }

    private void joinRoom(String roomId) {
        if (rtcVideo == null) {
            Log.e(TAG, "joinRoom skipped: rtcVideo is null");
            return;
        }
        if (rtcRoom != null) {
            rtcRoom.leaveRoom();
            rtcRoom.destroy();
            rtcRoom = null;
        }
        rtcRoom = rtcVideo.createRTCRoom(roomId);
        rtcRoom.setRTCRoomEventHandler(rtcRoomEventHandler);
        refreshNicknameDisplay();
        startRoomKeepLifeService();
        mHandler.post(createJoinRoomRunnable(roomId));
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
        RtcSessionController.getInstance().clearSession();
        applyJoinResultIcon(R.drawable.icon_failed);
        if (rtcRoom != null) {
            rtcRoom.leaveRoom();
            rtcRoom.destroy();
            rtcRoom = null;
            stopRoomKeepLifeService();
        }
        isJoined = false;
        btnJoinRoom.setText(getString(R.string.rtc_join_room));
        networkQuality.setVisibility(View.GONE);
        networkQuality.setText("");
        lockRoomInput();
        btnClearCache.setVisibility(View.VISIBLE);
        btnOpenFloatWindow.setVisibility(View.GONE);
    }

    private void scheduleRejoinRoom(String roomId) {
        if (!isLoopJoinRoom || TextUtils.isEmpty(roomId) || rtcVideo == null) {
            return;
        }
        mHandler.postDelayed(() -> {
            if (isLoopJoinRoom && rtcVideo != null) {
                joinRoom(roomId);
            }
        }, 1500);
    }

    private final IRTCVideoEventHandler rtcVideoEventHandler = new IRTCVideoEventHandler() {
        @Override
        public void onAudioRouteChanged(AudioRoute route) {
            super.onAudioRouteChanged(route);
            RtcToastUtil.showLongToast(RawAudioDataActivity.this,
                    getString(R.string.rtc_audio_route_changed, audioTypeMap.get(route)));
        }
    };

    private final IRTCRoomEventHandler rtcRoomEventHandler = new IRTCRoomEventHandler() {
        @Override
        public void onNetworkQuality(NetworkQualityStats localQuality, NetworkQualityStats[] remoteQualities) {
            super.onNetworkQuality(localQuality, remoteQualities);
            networkQuality.setVisibility(View.VISIBLE);
            networkQuality.setText(getString(R.string.rtc_network_quality,
                    NetworkQualityText.of(localQuality.txQuality),
                    NetworkQualityText.of(localQuality.rxQuality)));
        }

        @Override
        public void onRoomStateChanged(String roomId, String uid, int state, String extraInfo) {
            super.onRoomStateChanged(roomId, uid, state, extraInfo);
            Log.w(TAG, "onRoomStateChanged roomId=" + roomId + " uid=" + uid
                    + " state=" + state + " extraInfo=" + extraInfo);
            if (state != 0) {
                String detail = TextUtils.isEmpty(extraInfo) ? getString(R.string.rtc_conn_failed) : extraInfo;
                if (state == -1000) {
                    detail = getString(R.string.rtc_token_invalid_code);
                }
                if (isJoined && isLoopJoinRoom) {
                    RtcToastUtil.showLongToast(RawAudioDataActivity.this,
                            getString(R.string.rtc_room_rejoining, state));
                    scheduleRejoinRoom(roomId);
                    return;
                }
                RtcToastUtil.showAlert(RawAudioDataActivity.this,
                        getString(R.string.rtc_room_join_failed, state, detail));
                leaveRoom();
                return;
            }
            isJoined = true;
            isLoopJoinRoom = true;
            bindRtcSession();
            RtcToastUtil.showShortToast(RawAudioDataActivity.this, getString(R.string.rtc_room_joined));
            applyJoinResultIcon(R.drawable.icon_success);
            lockRoomInput();
        }

        @Override
        public void onLeaveRoom(RTCRoomStats stats) {
            super.onLeaveRoom(stats);
            lockRoomInput();
            RtcToastUtil.showLongToast(RawAudioDataActivity.this, "onLeaveRoom, stats:" + stats.toString());
        }

        @Override
        public void onRoomStats(RTCRoomStats stats) {
            super.onRoomStats(stats);
            onlineUsers = stats.users;
            @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String formattedDate = sdf.format(new Date());
            onlineUsersCountTextView.setText(getString(R.string.rtc_online_users, onlineUsers, formattedDate));
        }
    };

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
        updateCheckInStatus();
        refreshNicknameDisplay();
        if (isJoined && rtcVideo != null) {
            bindRtcSession();
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
                RtcSessionController.getInstance().onPhoneCallStateChanged(active);
                if (active && RtcSessionController.getInstance().isUsbAudioConnected()) {
                    RtcToastUtil.showShortToast(RawAudioDataActivity.this,
                            getString(R.string.rtc_phone_call_usb_bridge));
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

    private String getNickname() {
        String nickname = getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), MODE_PRIVATE)
                .getString(Constants.getSharedPrefsKeys_NICKNAME(), "NULL");
        if (nickname == null || nickname.isEmpty() || "NULL".equals(nickname)) {
            return DeviceUtils.getAndroidId(this);
        }
        return nickname;
    }

    private void refreshNicknameDisplay() {
        if (usernameTextView != null) {
            usernameTextView.setText(getNickname());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterPhoneCallReceiver();
        if (usbAudioDetector != null) {
            usbAudioDetector.stop();
            usbAudioDetector = null;
        }
        RtcSessionController.getInstance().clearSession();
        if (rtcVideo != null) {
            rtcVideo.stopAudioCapture();
            rtcVideo.stopVideoCapture();
        }
        if (rtcRoom != null) {
            rtcRoom.destroy();
            rtcRoom = null;
        }
        RTCVideo.destroyRTCVideo();
    }

    private void addFloatingWindow() {
        localViewContainer.removeView(textureView);
        floatWindowManager.openWindow();
    }

    private void closeFloatingWindow() {
        floatWindowManager.closeWindow();
        Intent intent = new Intent(this, RawAudioDataActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void requestFloatingWindowPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_FLOATING_WINDOW);
        } else {
            addFloatingWindow();
        }
    }

    private void warnIfTokenAppIdMismatch(String rtcToken) {
        String tokenAppId = RtcTokenUtil.extractAppId(rtcToken);
        if (TextUtils.isEmpty(tokenAppId) || TextUtils.equals(tokenAppId, Constants.RTC_APP_ID)) {
            return;
        }
        Log.w(TAG, "token appId differs from tophone_world: token=" + tokenAppId
                + " server=" + Constants.RTC_APP_ID);
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
        joinLoadingText.setText(message);
        joinLoadingText.setVisibility(View.VISIBLE);
        loadingIndicator.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        joinLoadingText.setVisibility(View.GONE);
        loadingIndicator.setVisibility(View.GONE);
    }

    private void showJoinLoading() {
        showLoading(getString(R.string.rtc_verifying_room));
        btnJoinRoom.setEnabled(false);
        btnClearCache.setEnabled(false);
        lockRoomInput();
    }

    private void hideJoinLoading() {
        hideLoading();
        btnJoinRoom.setEnabled(true);
        btnClearCache.setEnabled(true);
        lockRoomInput();
    }

    private void access() {
        hideLoading();
        btnClearCache.setEnabled(true);
        btnJoinRoom.setEnabled(true);
    }
}
