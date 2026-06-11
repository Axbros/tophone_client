package com.openim.tophone.rtc;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
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
import com.ss.bytertc.engine.type.AudioScenarioType;
import com.ss.bytertc.engine.type.ChannelProfile;
import com.ss.bytertc.engine.type.ConnectionState;
import com.ss.bytertc.engine.type.MediaTypeEnhancementConfig;
import com.ss.bytertc.engine.type.NetworkQualityStats;
import com.ss.bytertc.engine.type.RTCRoomStats;
import com.ss.bytertc.engine.utils.IAudioFrame;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RawAudioDataActivity extends RtcBaseActivity {

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

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
    private boolean isLoopJoinRoom;

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
    private Button btnDialog;
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
        showLoading(getString(R.string.rtc_loading_config));
        cacheUtil = new RtcCacheUtil(this);
        checkAndLoadConfig();
        access();
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
    }

    private void checkAndLoadConfig() {
        String cachedAppId = cacheUtil.getKeyAppId();
        if (!TextUtils.isEmpty(cachedAppId)) {
            Constants.RTC_APP_ID = cachedAppId;
            RtcToastUtil.showLongToast(RawAudioDataActivity.this, getString(R.string.rtc_using_cached_config));
        } else {
            Log.d(TAG, "缓存不存在，从服务器获取配置");
            fetchConfigFromServer();
        }
    }

    private void fetchConfigFromServer() {
        Request request = new Request.Builder()
                .url(Constants.getRtcConfigURL())
                .addHeader("Content-Type", "application/json")
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取配置失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseData = response.body().string();
                    Log.d(TAG, "服务器返回: " + responseData);

                    try {
                        org.json.JSONObject jsonObject = new org.json.JSONObject(responseData);
                        int code = jsonObject.getInt("code");

                        if (code == 0) {
                            org.json.JSONObject data = jsonObject.getJSONObject("data");
                            String appID = data.getString("appID");
                            if (!TextUtils.isEmpty(appID)) {
                                saveConfigToCache(appID);
                                mHandler.post(() -> {
                                    Constants.RTC_APP_ID = appID;
                                    RtcToastUtil.showLongToast(RawAudioDataActivity.this, getString(R.string.rtc_config_loaded));
                                });
                            } else {
                                Log.e(TAG, "解密失败，appID为空");
                                RtcToastUtil.showLongToast(RawAudioDataActivity.this, getString(R.string.rtc_decrypt_failed));
                            }
                        } else {
                            String msg = jsonObject.getString("msg");
                            Log.e(TAG, "接口返回错误: " + msg);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析数据失败: " + e.getMessage());
                        RtcToastUtil.showLongToast(RawAudioDataActivity.this, getString(R.string.rtc_parse_failed, e.getMessage()));
                    }
                } else {
                    Log.e(TAG, "服务器返回错误: " + response.code());
                    RtcToastUtil.showLongToast(RawAudioDataActivity.this, getString(R.string.rtc_server_error, response.code()));
                }
            }
        });
    }

    private void saveConfigToCache(String appId) {
        cacheUtil.saveAppID(appId);
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
        btnDialog = findViewById(R.id.btn_open_message_dialog);
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

                        initRTCVideo();
                        btnDialog.setVisibility(View.VISIBLE);
                        btnClearCache.setVisibility(View.GONE);
                        token = t;
                        joinRoom(roomId);
                        isJoined = true;
                        isLoopJoinRoom = true;
                        audioFrameCallbackSwitch.setEnabled(true);
                        btnJoinRoom.setText(getString(R.string.rtc_leave_room));
                        btnJoinRoom.setBackgroundColor(Color.parseColor("#E91E63"));
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        hideJoinLoading();
                        RtcToastUtil.showAlert(RawAudioDataActivity.this, getString(R.string.rtc_verify_room_failed, e.getMessage()));
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
            rtcVideo.setAudioScenario(AudioScenarioType.AUDIO_SCENARIO_COMMUNICATION);

            if (isChecked) {
                int result = rtcVideo.setAudioRoute(AudioRoute.AUDIO_ROUTE_HEADSET);
                if (result != 0) {
                    rtcVideo.setAudioRoute(AudioRoute.AUDIO_ROUTE_EARPIECE);
                }
            } else {
                rtcVideo.setAudioRoute(AudioRoute.AUDIO_ROUTE_SPEAKERPHONE);
            }
        });

        btnDialog.setOnClickListener(v -> {
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_send_message, null);

            EditText input = dialogView.findViewById(R.id.dialog_message_input);
            Button btnSend = dialogView.findViewById(R.id.dialog_send_btn);
            Button btnCancel = dialogView.findViewById(R.id.dialog_cancel_btn);

            AlertDialog dialog = new AlertDialog.Builder(RawAudioDataActivity.this)
                    .setView(dialogView)
                    .create();

            btnSend.setOnClickListener(view -> {
                String msg = input.getText().toString().trim();
                if (!msg.isEmpty()) {
                    RtcToastUtil.showShortToast(RawAudioDataActivity.this, getString(R.string.rtc_notifying_dispatch));
                    sendMessageToRoom(roomIdInput.getText().toString(), msg);
                    dialog.dismiss();
                }
            });

            btnCancel.setOnClickListener(view -> dialog.dismiss());
            dialog.show();
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
            rtcRoom.setRTCRoomEventHandler(rtcRoomEventHandler);
        };
    }

    private void joinRoom(String roomId) {
        rtcRoom = rtcVideo.createRTCRoom(roomId);
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
        btnDialog.setVisibility(View.GONE);
        mHandler.removeCallbacks(createJoinRoomRunnable(roomIdInput.getText().toString()));
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
            if (state != 0) {
                if (isLoopJoinRoom) {
                    RtcToastUtil.showLongToast(RawAudioDataActivity.this,
                            getString(R.string.rtc_room_rejoining, state));
                    return;
                }
                RtcToastUtil.showAlert(RawAudioDataActivity.this,
                        getString(R.string.rtc_room_join_failed, extraInfo));
                leaveRoom();
            } else {
                RtcToastUtil.showShortToast(RawAudioDataActivity.this, getString(R.string.rtc_room_joined));
                applyJoinResultIcon(R.drawable.icon_success);
                lockRoomInput();
            }
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
    protected void onResume() {
        super.onResume();
        updateCheckInStatus();
        refreshNicknameDisplay();
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

    private void sendMessageToRoom(String roomId, String msg) {
        String json = "{"
                + "\"roomID\":\"" + roomId + "\","
                + "\"message\":\"" + msg + "\""
                + "}";

        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(Constants.getNotifyRoomManagerURL())
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        new Thread(() -> {
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    runOnUiThread(() ->
                            RtcToastUtil.showLongToast(RawAudioDataActivity.this, getString(R.string.rtc_message_sent))
                    );
                } else {
                    runOnUiThread(() ->
                            RtcToastUtil.showLongToast(RawAudioDataActivity.this, getString(R.string.rtc_message_send_failed, response.code()))
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        RtcToastUtil.showLongToast(RawAudioDataActivity.this, getString(R.string.rtc_network_error, e.getMessage()))
                );
            }
        }).start();
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
