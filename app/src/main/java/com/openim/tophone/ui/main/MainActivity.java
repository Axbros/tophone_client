package com.openim.tophone.ui.main;


import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.openim.tophone.R;
import com.openim.tophone.base.BaseActivity;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.databinding.ActivityMainBinding;

import com.openim.tophone.openim.IMUtil;
import com.openim.tophone.openim.entity.LoginCertificate;
import com.openim.tophone.service.ForegroundService;
import com.openim.tophone.stroage.VMStore;
import com.openim.tophone.ui.main.vm.UserVM;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DeviceUtils;
import com.openim.tophone.utils.L;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.openim.android.sdk.OpenIMClient;


public class MainActivity extends BaseActivity<UserVM, ActivityMainBinding> {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static String machineCode;
    private static String TAG = "MainActivity";
    private static LoginCertificate certificate = LoginCertificate.getCache(BaseApp.inst());
    ;
    private ActivityMainBinding view;

    private String phoneNumber;

    public static SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        bindVM(UserVM.class);
//    官方代码 不可用 下面三个是自己的代码    bindViewDataBinding(ActivityMainBinding.inflate(getLayoutInflater()));
        view = DataBindingUtil.setContentView(this, R.layout.activity_main);
        view.setUserVM(vm);
        view.setLifecycleOwner(this);
        VMStore.init(vm);
        super.onCreate(savedInstanceState);
        //初始化UI
        initPermissions();
        init(getApplicationContext());
        initStorage();
        requestDefaultDialer();
        //初始化openim
        initOpenIM();
        initObserve();
        initSMSListener();

        EdgeToEdge.enable(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 123) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "设置为默认拨号器成功！", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "未设置为默认拨号器，无法获取号码", Toast.LENGTH_LONG).show();
                new AlertDialog.Builder(this)
                        .setTitle("权限提示")
                        .setMessage("为了正常获取来电号码，需要将本应用设置为默认拨号器，是否前往设置？")
                        .setPositiveButton("前往设置", (dialog, which) -> {
                            Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void initOpenIM() {
        BaseApp.inst().loginCertificate = certificate;
        vm.login(machineCode);
    }

    public static String getLoginEmail(){
        return machineCode;
    }
    public void handleAccountIDClick(View view) {
        System.out.println(view);
        try{
            if (Objects.equals(vm.accountID.get(), machineCode)) {
//                vm.accountID.set(certificate.getNickname());
                String nickname= sp.getString(Constants.getSharedPrefsKeys_NICKNAME(),"404");
                vm.accountID.set(nickname);

            } else {
                TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);


                vm.accountID.set(phoneNumber.isEmpty()?machineCode:phoneNumber );
            }
        }catch (Exception e){
            L.e(TAG,e.getMessage());
        }
    }

    public void init(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            machineCode = DeviceUtils.getAndroidId(context);
            return;
        }
        machineCode = telephonyManager.getLine1Number();

        machineCode=machineCode.replace("+86","");

        vm.accountID.set(machineCode);
        //观察者模式 观察 account status
        // 2.查询当前设备是否注册
        vm.checkIfUserExists(machineCode);

    }


    private void initStorage(){
        sp = BaseApp.inst().getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(),Context.MODE_PRIVATE);
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

    public void handleUploadLogsBtnClick(View v){
        OpenIMClient.getInstance().uploadLogs(new IMUtil.IMCallBack<String>(){
            @Override
            public void onSuccess(String data) {
                Toast.makeText(BaseApp.inst(),"✅Upload successed!",Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onError(int code, String error) {
                Toast.makeText(BaseApp.inst(),"❌Upload failed!"+error,Toast.LENGTH_SHORT).show();
                L.e("IMCallBack", "uploadLogs onError:(" + code + ")" + error);
                LoginCertificate.clear();
            }
        },new ArrayList<>(),500,"",(l, l1) -> {
            L.d("testprogress", "current:" + l + "total:" + l1);
        });
    }

    private void initPermissions() {


        List<String> permissionsToRequest = new ArrayList<>();
        //添加通知权限

        Intent intent = new Intent();
        String packageName = getPackageName();
        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE},
                    PERMISSION_REQUEST_CODE);
        } else {
            Intent serviceIntent = new Intent(this, ForegroundService.class);
            startForegroundService(serviceIntent);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, 888);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，请求权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE},
                    PERMISSION_REQUEST_CODE);
        }

        // 过滤电池优化权限

        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + packageName));
        startActivity(intent);
        // 添加网络权限

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.INTERNET);
        }

        // 检查直接拨打电话权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CALL_PHONE);
        }

        // 检查接听电话权限（注意：接听电话权限在 Android 8.0 及以上版本需要特殊处理）
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ANSWER_PHONE_CALLS);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
                == PackageManager.PERMISSION_GRANTED) {
            vm.phonePermissions.set(true);
        }

        // 检查发送短信权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS);
        }

        // 检查监听收到短信权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            vm.smsPermissions.set(true);
        }

        // 如果有需要请求的权限
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            // 所有权限都已授予
            Toast.makeText(this, "所有权限已授予", Toast.LENGTH_SHORT).show();
        }


    }

    //permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Some permissions not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void initObserve() {
        Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
        intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
        startActivity(intent);

    }
    public void requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (telecomManager != null && !getPackageName().equals(telecomManager.getDefaultDialerPackage())) {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                startActivityForResult(intent, 123); // 可以用来回调判断是否成功
            } else {
                Toast.makeText(this, "当前已是默认拨号器", Toast.LENGTH_SHORT).show();
            }
        }
    }
    public void initSMSListener(){
        // 检查是否已经有读取短信的权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，请求权限
            vm.smsPermissions.set(false);
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_SMS},
                    PERMISSION_REQUEST_CODE);
        } else {
            vm.smsPermissions.set(true);

        }

    }

}