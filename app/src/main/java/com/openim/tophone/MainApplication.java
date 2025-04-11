package com.openim.tophone;

import android.content.Intent;
import android.os.Build;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.base.vm.injection.Easy;
import com.openim.tophone.net.RXRetrofit.HttpConfig;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.openim.IM;
import com.openim.tophone.openim.entity.LoginCertificate;
import com.openim.tophone.openim.vm.UserLogic;
import com.openim.tophone.utils.ActivityManager;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.L;
import com.openim.tophone.service.PhoneState;
import java.io.File;

import io.openim.android.sdk.BuildConfig;

import okhttp3.Request;


public class MainApplication extends BaseApp{
    private static final String TAG = BaseApp.class.getSimpleName();

    @Override
    public void onCreate() {
        L.e(TAG, "-----onCreate------");
        super.onCreate();
        initFile();
        initController();
        initNet();
        initIM();
        initService();
    }


    private void initFile() {
        buildDirectory(Constants.File_DIR);
    }

    private boolean buildDirectory(String path) {
        File file = new File(path);
        if (file.exists())
            return true;
        return file.mkdirs();
    }

    private void initController() {
        Easy.installVM(UserLogic.class);
    }


    private void initNet() {
        N.init(new HttpConfig().setBaseUrl(Constants.getAppAuthUrl()).setDebug(BuildConfig.DEBUG)
                .addInterceptor(chain -> {
                    String token = "";
                    try {
                        token = BaseApp.inst().loginCertificate.chatToken;
                    } catch (Exception ignored) {
                    }
                    Request request = chain.request().newBuilder()
                            .addHeader("token", token)
                            .addHeader("operationID", String.valueOf(System.currentTimeMillis()))
                            .build();
                    return chain.proceed(request);
                }));
    }

    private void initIM() {
        IM.initSdk(this);
    }


    public void offline() {
        LoginCertificate.clear();
        ActivityManager.finishAllExceptActivity();

// 跳转网页 网页提示 感谢您的使用 下次再见
//        startActivity(new Intent(BaseApp.inst(), LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }


    public void initService(){
        if (!PhoneState.isLive()) {
            // 8.0前后启动前台服务的方法不同
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent intent = new Intent(this, PhoneState.class);
                startForegroundService(intent);
            } else {
                Intent intent = new Intent(this, PhoneState.class);
                startService(intent);
            }
        }
    }

}



