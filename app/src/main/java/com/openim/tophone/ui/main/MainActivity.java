package com.openim.tophone.ui.main;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import com.openim.tophone.base.vm.State;
import com.openim.tophone.base.vm.injection.Easy;
import com.openim.tophone.databinding.ActivityMainBinding;

import com.openim.tophone.openim.IMEvent;
import com.openim.tophone.openim.entity.LoginCertificate;
import com.openim.tophone.openim.vm.UserLogic;
import com.openim.tophone.ui.main.vm.UserVM;
import com.openim.tophone.utils.DeviceUtils;
import com.openim.tophone.utils.L;

import java.util.Observable;
import java.util.Observer;
import java.util.ArrayList;
import java.util.List;



public class MainActivity extends BaseActivity<UserVM, ActivityMainBinding> implements Observer {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static String machineCode;
    private static String TAG = "MainActivity";
    private static LoginCertificate certificate = LoginCertificate.getCache(BaseApp.inst());
    ;
    private ActivityMainBinding view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        bindVM(UserVM.class);
//    官方代码 不可用 下面三个是自己的代码    bindViewDataBinding(ActivityMainBinding.inflate(getLayoutInflater()));
        view = DataBindingUtil.setContentView(this, R.layout.activity_main);
        view.setUserVM(vm);
        view.setLifecycleOwner(this);
        super.onCreate(savedInstanceState);

        //初始化UI
        initPermissions();
        init(getApplicationContext());

        //初始化openim
        initOpenIM();
        EdgeToEdge.enable(this);
    }

    public void initOpenIM() {
        BaseApp.inst().loginCertificate = certificate;
        UserLogic userLogic = Easy.find(UserLogic.class);
        if (certificate != null) {
            userLogic.loginCacheUser(userId -> {
                vm.accountStatus.setValue("Online");
                L.d(TAG, "Login User ID: " + userId);
                Toast.makeText(getBaseContext(), "Login User ID: " + userId + " Success!", Toast.LENGTH_SHORT).show();
            });
        } else {
            vm.login();
        }
    }


    public void handleAccountIDClick(View view) {
        if (vm.accountID.getValue() == machineCode) {
            vm.accountID.setValue(certificate.nickname);
        } else {
            vm.accountID.setValue(machineCode);
        }
    }

    public void init(Context context) {

        // 1.获取设备 ID
        machineCode = DeviceUtils.getAndroidId(context) + "@q36q.edu.cn";
        vm.setAccountID(new State<>(machineCode));
        //观察者模式 观察 account status
        // 2.查询当前设备是否注册
        vm.checkIfUserExists(machineCode);







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
        System.out.println(BaseApp.inst().loginCertificate);
        vm.connect();

        System.out.println("按下了button");

    }

    private void initPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                permissionsToRequest.add(Manifest.permission.ANSWER_PHONE_CALLS);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
                == PackageManager.PERMISSION_GRANTED) {
            vm.setPhonePermissions(new State<>(true));
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
            vm.setSmsPermissions(new State<>(true));
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

    @Override
    public void update(Observable o, Object arg) {

    }
}