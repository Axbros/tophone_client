package com.openim.tophone.ui.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.graphics.Bitmap;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.openim.tophone.MainApplication;
import com.openim.tophone.R;
import com.openim.tophone.rtc.RtcDebugLog;
import com.openim.tophone.base.BaseActivity;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.databinding.ActivityMainBinding;
import com.openim.tophone.stroage.VMStore;
import com.openim.tophone.ui.main.vm.UserVM;
import com.openim.tophone.rtc.RawAudioDataActivity;
import com.openim.tophone.rtc.RtcBackgroundJoiner;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DeviceUtils;
import com.openim.tophone.utils.L;
import com.openim.tophone.utils.AppToast;
import com.openim.tophone.utils.PhoneStateService;
import com.openim.tophone.utils.QrCodeHelper;
import com.openim.tophone.utils.SharedPreferencesUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

public class MainActivity extends BaseActivity<UserVM, ActivityMainBinding> {
    private static final int PERMISSION_REQUEST_CODE = 1;
    public static String machineCode;
    private static String TAG = "MainActivity";
    public static SharedPreferences sp;
    private TextView callLogStatisticText;
    private ImageView pairingQrImage;
    private SwitchCompat roomSwitch;
    private View baiduCloakOverlay;
    private WebView baiduCloakWebView;
    private ProgressBar baiduCloakLoading;

    private static Button connectBtn;

    private boolean roomSwitchInternal;


    private int clickCount = 0;
    private long lastClickTime = 0;
    private int rtcClickCount = 0;
    private long rtcLastClickTime = 0;
    private int baiduCloakClickCount = 0;
    private long baiduCloakLastClickTime = 0;

    private static final String BAIDU_CLOAK_URL = "https://www.baidu.com";
    // 给用户足够时间完成三次点击，避免正常点击节奏被误判为新的序列。
    private static final long BAIDU_CLOAK_CLICK_WINDOW_MS = 2500L;

    public static void seBtnConnectDisable(){
        if (connectBtn != null) {
            connectBtn.setEnabled(false);
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding view = DataBindingUtil.setContentView(this, R.layout.activity_main);
        setupBaiduCloak();
        callLogStatisticText = findViewById(R.id.call_log_statistic_text);
        pairingQrImage = findViewById(R.id.pairing_qr_image);
        View headerBGImage = findViewById(R.id.header_include);
        setupHiddenDomainEntry(headerBGImage);
        findViewById(R.id.link_server_settings).setOnClickListener(v ->
                startActivity(new Intent(this, DomainConfigActivity.class)));
        setupHiddenRtcEntry(callLogStatisticText);
        roomSwitch = findViewById(R.id.room_switch);
        setupRoomSwitch();
        connectBtn = findViewById(R.id.btn_connect);
        // 格式化字符串并设置
        int currentYear = Calendar.getInstance().get(Calendar.YEAR) ;
        TextView textView = findViewById(R.id.copyright);
        textView.setText(getString(R.string.learn_more, currentYear));
        // 2. 初始化 ViewModel
        vm = new ViewModelProvider(this).get(UserVM.class);
        view.setUserVM(vm);
        view.setLifecycleOwner(this);
        VMStore.init(vm);
        vm.showPairingQr.observe(this, this::refreshPairingQr);
        vm.showBoundFeatures.observe(this, this::onBoundFeaturesChanged);

        ((MainApplication) getApplication()).startBootstrap();
        startAppInitialization();

    }

    public static String getLoginEmail() {
        return machineCode;
    }

    public void handleAccountIDClick(View view) {
        vm.isLoading.setValue(true);
        try {
            String displayUsername = getDisplayUsername();
            String current = vm.accountID.getValue();
            if (Objects.equals(current, displayUsername)) {
                vm.accountID.setValue(machineCode);
            } else {
                vm.accountID.setValue(displayUsername);
            }
        } catch (Exception e) {
            L.e(TAG, e.getMessage());
        }
        vm.isLoading.setValue(false);
    }

    private String getDisplayUsername() {
        if (sp == null) {
            return machineCode != null ? machineCode : "";
        }
        String username = sp.getString(Constants.getNormalUsernameKey(), "");
        if (TextUtils.isEmpty(username)) {
            return machineCode != null ? machineCode : "";
        }
        return username;
    }

    public void init() {

        machineCode = DeviceUtils.getOrCreateClientDeviceId(BaseApp.inst());
        if (machineCode == null || machineCode.isEmpty()) {
            AppToast.show(BaseApp.inst(), R.string.toast_device_id_missing, Toast.LENGTH_LONG);
            return;
        }
        checkAndRequestPermissions();
        String savedUsername = sp != null
                ? sp.getString(Constants.getNormalUsernameKey(), "")
                : "";
        if (!TextUtils.isEmpty(savedUsername)) {
            vm.accountID.setValue(savedUsername);
        } else {
            vm.accountID.setValue(machineCode);
        }
    }

    private void initStorage() {
        sp = BaseApp.inst().getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE);
    }

    // 实现 openLink 方法
    public void openLink(View view) {
        // 这里假设要打开的链接是 www.tophone.cc
        String url = "https://www.tophone.cc";
        // 创建一个 Intent，用于打开链接
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        // 检查是否有应用程序可以处理该 Intent
        if (intent.resolveActivity(getPackageManager()) != null) {
            // 启动该 Intent
            startActivity(intent);
        }
    }

    public void handleConnect(View v) {
        vm.handleBtnConnect();
    }


    private boolean checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        }

        // 常规权限列表
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_PHONE_NUMBERS);
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CALL_PHONE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ANSWER_PHONE_CALLS);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CALL_LOG);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // 如果还有权限没获取，发起请求
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
            return false;
        }

        // 权限都已授予，执行后续初始化逻辑
        onPermissionsReady(true);
        return true;
    }

    private void onPermissionsReady(boolean allGranted) {
        if (allGranted) {
            handlePostPermissionLogic();
        } else {
            AppToast.show(this, R.string.toast_permissions_denied, Toast.LENGTH_LONG);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                AppToast.show(this, R.string.toast_permissions_granted, Toast.LENGTH_SHORT);
                onPermissionsReady(true);
            } else {
                onPermissionsReady(false);
            }
        }
    }

    private void handlePostPermissionLogic() {
        if (Boolean.TRUE.equals(vm.showBoundFeatures.getValue())) {
            startPhoneStateServiceSafely();
        }
        promptDefaultDialerIfNeeded();
        MainApplication app = (MainApplication) getApplication();
        app.triggerDeviceProfileRefresh();
    }

    private boolean hasPhonePermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startPhoneStateServiceSafely() {
        if (!hasPhonePermissions()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            Intent serviceIntent = new Intent(this, PhoneStateService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            L.e(TAG, "start PhoneStateService failed: " + e.getMessage());
            AppToast.show(this, R.string.toast_phone_service_failed, Toast.LENGTH_LONG);
        }
    }

    private void startAppInitialization() {
        initStorage();
        init();
        vm.syncCheckInStatus(this);
        initSMSListener();
        // 默认拨号器设置延后到权限就绪后，避免首次打开立即跳转系统页
    }

    private void promptDefaultDialerIfNeeded() {
        try {
            Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            L.w(TAG, "prompt default dialer skipped: " + e.getMessage());
        }
    }

    public void initSMSListener() {
        // SMS 系统权限与后台策略状态无关，仅用于本地监听
    }


    private void refreshPairingQr(Boolean show) {
        if (pairingQrImage == null || !Boolean.TRUE.equals(show)) {
            return;
        }
        String deviceCode = machineCode;
        if (deviceCode == null || deviceCode.isEmpty()) {
            deviceCode = DeviceUtils.getOrCreateClientDeviceId(this);
        }
        if (deviceCode == null || deviceCode.isEmpty()) {
            return;
        }
        String payload = QrCodeHelper.buildDevicePairingPayload(deviceCode);
        Bitmap bitmap = QrCodeHelper.encode(payload, 512);
        if (bitmap != null) {
            pairingQrImage.setImageBitmap(bitmap);
        }
    }

    private void refreshCallLogStatistic() {
        if (callLogStatisticText == null) {
            return;
        }
        SharedPreferencesUtil prefs = SharedPreferencesUtil.get(this);
        callLogStatisticText.setText(prefs.formatTodayCallStats());
    }

    private boolean callLogReceiverRegistered = false;

    private void setupRoomSwitch() {
        if (roomSwitch == null) {
            return;
        }
        RtcBackgroundJoiner.get().setListener((joined, joining) ->
                runOnUiThread(() -> syncRoomSwitch()));
        roomSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (roomSwitchInternal) {
                return;
            }
            if (isChecked) {
                RtcBackgroundJoiner.get().requestJoin();
            } else {
                RtcBackgroundJoiner.get().leaveRoom();
            }
        });
    }

    private void syncRoomSwitch() {
        if (roomSwitch == null) {
            return;
        }
        roomSwitchInternal = true;
        roomSwitch.setChecked(RtcBackgroundJoiner.get().shouldSwitchBeOn());
        roomSwitchInternal = false;
    }

    private void onBoundFeaturesChanged(Boolean bound) {
        if (Boolean.TRUE.equals(bound)) {
            refreshCallLogStatistic();
            if (hasPhonePermissions()) {
                startPhoneStateServiceSafely();
            }
            registerCallLogReceiverIfNeeded();
        } else {
            unregisterCallLogReceiverIfNeeded();
        }
    }

    private void registerCallLogReceiverIfNeeded() {
        if (callLogReceiverRegistered) {
            return;
        }
        registerReceiver(receiver, new IntentFilter("CALL_LOG_EVENT"));
        callLogReceiverRegistered = true;
    }

    private void unregisterCallLogReceiverIfNeeded() {
        if (!callLogReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
        callLogReceiverRegistered = false;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String callType = intent.getStringExtra("type");
            if (callType != null) {
                SharedPreferencesUtil.get(MainActivity.this).recordCallEvent(callType);
            }
            refreshCallLogStatistic();
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        vm.syncCheckInStatus(this);
        if (Boolean.TRUE.equals(vm.showBoundFeatures.getValue())) {
            registerCallLogReceiverIfNeeded();
            refreshCallLogStatistic();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        vm.syncCheckInStatus(this);
        if (Boolean.TRUE.equals(vm.showBoundFeatures.getValue())) {
            refreshCallLogStatistic();
        }
        ((MainApplication) getApplication()).triggerDeviceProfileRefresh();
        if (RtcDebugLog.hasCrashReport()) {
            AppToast.show(this, R.string.rtc_debug_crash_main_hint, Toast.LENGTH_LONG);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterCallLogReceiverIfNeeded();
    }

    @Override
    protected void onDestroy() {
        RtcBackgroundJoiner.get().setListener(null);
        destroyBaiduCloak();
        super.onDestroy();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupBaiduCloak() {
        AppToast.setCloakVisible(true);
        baiduCloakOverlay = findViewById(R.id.baidu_cloak_overlay);
        baiduCloakWebView = findViewById(R.id.baidu_cloak_webview);
        baiduCloakLoading = findViewById(R.id.baidu_cloak_loading);
        View header = findViewById(R.id.baidu_cloak_header);
        if (baiduCloakOverlay == null || baiduCloakWebView == null || header == null) {
            return;
        }

        WebSettings settings = baiduCloakWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        baiduCloakWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (baiduCloakLoading != null) {
                    baiduCloakLoading.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (baiduCloakLoading != null) {
                    baiduCloakLoading.setVisibility(View.GONE);
                }
            }
        });
        baiduCloakWebView.setWebChromeClient(new WebChromeClient());
        baiduCloakWebView.loadUrl(BAIDU_CLOAK_URL);

        header.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - baiduCloakLastClickTime > BAIDU_CLOAK_CLICK_WINDOW_MS) {
                baiduCloakClickCount = 0;
            }
            baiduCloakLastClickTime = now;
            baiduCloakClickCount++;
            if (baiduCloakClickCount >= 3) {
                baiduCloakClickCount = 0;
                revealDeviceApp();
            }
        });
    }

    private void revealDeviceApp() {
        if (baiduCloakOverlay == null) {
            return;
        }
        AppToast.setCloakVisible(false);
        baiduCloakOverlay.setVisibility(View.GONE);
        if (baiduCloakWebView != null) {
            baiduCloakWebView.stopLoading();
            baiduCloakWebView.loadUrl("about:blank");
        }
    }

    private void destroyBaiduCloak() {
        if (baiduCloakWebView == null) {
            return;
        }
        baiduCloakWebView.stopLoading();
        baiduCloakWebView.setWebChromeClient(null);
        baiduCloakWebView.setWebViewClient(null);
        baiduCloakWebView.destroy();
        baiduCloakWebView = null;
    }

    private void setupHiddenDomainEntry(View targetView) {
        targetView.setOnClickListener(v -> {
            long now = System.currentTimeMillis();

            // 超过 800ms 认为是一次新的点击序列
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

    /** 连点 7 次进入控制端语聊房 */
    private void setupHiddenRtcEntry(View targetView) {
        targetView.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - rtcLastClickTime > 800) {
                rtcClickCount = 0;
            }
            rtcLastClickTime = now;
            rtcClickCount++;
            if (rtcClickCount >= 7) {
                rtcClickCount = 0;
                startActivity(new Intent(this, RawAudioDataActivity.class));
            }
        });
    }
}
