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
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.openim.tophone.R;
import com.openim.tophone.base.BaseActivity;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.databinding.ActivityMainBinding;
import com.openim.tophone.enums.CallLogType;
import com.openim.tophone.openim.IM;
import com.openim.tophone.openim.entity.LoginCertificate;
import com.openim.tophone.stroage.VMStore;
import com.openim.tophone.ui.main.vm.UserVM;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DeviceUtils;
import com.openim.tophone.utils.L;
import com.openim.tophone.utils.PhoneStateService;
import com.openim.tophone.utils.SharedPreferencesUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

public class MainActivity extends BaseActivity<UserVM, ActivityMainBinding> {
    private static final int PERMISSION_REQUEST_CODE = 1;
    public static String machineCode;
    private static String TAG = "MainActivity";
    private static LoginCertificate certificate = LoginCertificate.getCache(BaseApp.inst());
    ;
    public static SharedPreferences sp;
    private TextView callLogStatisticText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding view = DataBindingUtil.setContentView(this, R.layout.activity_main);
        callLogStatisticText = findViewById(R.id.call_log_statistic_text);
        callLogStatisticText.setText("No Call Log Data Now");

        // 格式化字符串并设置
        int currentYear = Calendar.getInstance().get(Calendar.YEAR) ;
        TextView textView = findViewById(R.id.copyright);
        textView.setText(getString(R.string.learn_more, currentYear));
        // 2. 初始化 ViewModel
        vm = new ViewModelProvider(this).get(UserVM.class);
        view.setUserVM(vm);
        view.setLifecycleOwner(this);
        VMStore.init(vm);

        startAppInitialization();

    }

    public void initOpenIM() {
        vm.isLoading.setValue(true);
        BaseApp.inst().loginCertificate = certificate;
        vm.login(machineCode);
    }

    public static String getLoginEmail() {
        return machineCode;
    }

    public void handleAccountIDClick(View view) {
        vm.isLoading.setValue(true);
        try {
            if (Objects.equals(vm.accountID.getValue(), machineCode)) {
//                vm.accountID.setValue(certificate.getNickname());
                String nickname = sp.getString(Constants.getSharedPrefsKeys_NICKNAME(), "NULL");
                vm.accountID.setValue(nickname);
            } else {
                TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                vm.accountID.setValue(machineCode);
            }
        } catch (Exception e) {
            L.e(TAG, e.getMessage());
        }
        vm.isLoading.setValue(false);
    }

    public void init() {

        machineCode = DeviceUtils.getAndroidId(BaseApp.inst());
        if (machineCode == null) {
            Toast.makeText(BaseApp.inst(), "未能獲取到設備ID 請檢查是否具有對應權限！", Toast.LENGTH_LONG).show();
            return;
        }
        checkAndRequestPermissions();
//        machineCode=machineCode.substring(machineCode.length()-8);
        vm.accountID.setValue(machineCode);
        //观察者模式 观察 account status
        // 2.查询当前设备是否注册
        vm.checkIfUserExists(machineCode);


        // 启动并绑定Service
        Intent intent = new Intent(this, PhoneStateService.class);
        startService(intent);        // 启动Service

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

        // 常规权限列表
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE);
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CALL_LOG);
        }

        // ✅ 如果还有权限没获取，发起请求
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
            return false;
        }

        // ✅ 权限都已授予，执行后续初始化逻辑
        handlePostPermissionLogic();
        return true;
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
                Toast.makeText(this, "所有权限已授予", Toast.LENGTH_SHORT).show();
                handlePostPermissionLogic();
            } else {
                Toast.makeText(this, "部分权限未授予，应用可能无法正常运行", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void handlePostPermissionLogic() {
        // 启动 PhoneStateService
        Intent serviceIntent = new Intent(this, PhoneStateService.class);
        startForegroundService(serviceIntent);

        // 更新权限状态
        vm.phonePermissions.setValue(true);
        vm.smsPermissions.setValue(true);

        // 忽略电池优化（跳转设置）
//        Intent batteryIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
//        batteryIntent.setData(Uri.parse("package:" + getPackageName()));
//        startActivity(batteryIntent);

        // 打开通知设置页面（可选）
//        Intent notificationIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
//        notificationIntent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
//        startActivity(notificationIntent);

        // 执行初始化逻辑
//        startAppInitialization();
    }

    private void startAppInitialization() {

        IM.initSdk(BaseApp.inst());

        initStorage();          // 初始化 SharedPreferences
        init();  // 设置 machineCode、calllog、accountID、检查用户存在

//        requestDefaultDialer(); // 申请默认拨号器权限（系统弹窗）

        initOpenIM();           // OpenIM 登录
        initObserve();          // 设置观察者（如拨号器观察）
        initSMSListener();      // 短信权限状态设置
    }


    //permission

    public void initObserve() {
        Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
        intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
        startActivity(intent);
    }

    public void initSMSListener() {
        // 检查是否已经有读取短信的权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，请求权限
            vm.smsPermissions.setValue(false);
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_SMS},
                    PERMISSION_REQUEST_CODE);
        } else {
            vm.smsPermissions.setValue(true);
        }
    }


    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String number = intent.getStringExtra("number");
            String callType = intent.getStringExtra("type");

            SharedPreferencesUtil sharedPreferencesUtil = new SharedPreferencesUtil(MainActivity.this);
            sharedPreferencesUtil.checkAndResetDailyStats();
            int CALL_IN_TOTAL=sharedPreferencesUtil.getTodayCallInCount();
            int CALL_OUT_TOTAL = sharedPreferencesUtil.getTodayCallOutCount();
            if (callType != null) {
                if (CallLogType.CALL_IN.getDescription().equals(callType)) {
                    sharedPreferencesUtil.increaseCallInCount();
                    CALL_IN_TOTAL =sharedPreferencesUtil.getTodayCallInCount();
                } else if (CallLogType.CALL_OUT.getDescription().equals(callType)) {
                    sharedPreferencesUtil.increaseCallOutCount();
                    CALL_OUT_TOTAL = sharedPreferencesUtil.getTodayCallOutCount();
                }
            }

            // 更新 UI：用 String.format 插入变量
            @SuppressLint("DefaultLocale") String text = String.format("IN：%d  | OUT：%d ", CALL_IN_TOTAL, CALL_OUT_TOTAL);
            callLogStatisticText.setText(text);
        }

    };

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(receiver, new IntentFilter("CALL_LOG_EVENT"));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(receiver);
    }
}