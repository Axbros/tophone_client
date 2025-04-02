package com.openim.tophone;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.base.vm.injection.Easy;
import com.openim.tophone.net.RXRetrofit.HttpConfig;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.openim.IM;
import com.openim.tophone.openim.IMEvent;
import com.openim.tophone.openim.entity.LoginCertificate;
import com.openim.tophone.openim.vm.UserLogic;
import com.openim.tophone.utils.ActivityManager;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.L;

import java.io.File;

import io.openim.android.sdk.BuildConfig;
import io.openim.android.sdk.listener.OnConnListener;
import okhttp3.Request;


public class MainApplication extends BaseApp {
    private static final String TAG = BaseApp.class.getSimpleName();

    @Override
    public void onCreate() {
        L.e(TAG, "-----onCreate------");
        super.onCreate();

        initFile();
        initController();
        initNet();
        initIM();
    }


    private void initFile() {
        buildDirectory(Constants.AUDIO_DIR);
        buildDirectory(Constants.VIDEO_DIR);
        buildDirectory(Constants.PICTURE_DIR);
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
                    } catch (Exception ignored) {}
                    Request request = chain.request().newBuilder()
                            .addHeader("token", token)
                            .addHeader("operationID", String.valueOf(System.currentTimeMillis()))
                            .build();
                    return chain.proceed(request);
                }));
    }

    private void initIM() {
        IM.initSdk(this);
        listenerIMOffline();
    }

    private void listenerIMOffline() {
        IMEvent.getInstance().addConnListener(new OnConnListener() {
            @Override
            public void onConnectFailed(int code, String error) {

            }

            @Override
            public void onConnectSuccess() {

            }

            @Override
            public void onConnecting() {

            }

            @Override
            public void onKickedOffline() {
                offline();
            }

            @Override
            public void onUserTokenExpired() {
                offline();
            }

            @Override
            public void onUserTokenInvalid(String reason) {
                offline();
            }

        });
    }


    public void offline() {
        LoginCertificate.clear();

        ActivityManager.finishAllExceptActivity();

//        startActivity(new Intent(BaseApp.inst(), LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

}
