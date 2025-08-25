package com.openim.tophone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.base.vm.injection.Easy;
import com.openim.tophone.net.RXRetrofit.HttpConfig;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.openim.entity.CurrentVersionReq;
import com.openim.tophone.openim.entity.LoginCertificate;
import com.openim.tophone.openim.vm.UserLogic;
import com.openim.tophone.repository.CallLogApi;
import com.openim.tophone.utils.ActivityManager;
import com.openim.tophone.utils.AppVersionUtil;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DeviceUtils;
import com.openim.tophone.utils.L;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

import io.openim.android.sdk.BuildConfig;
import okhttp3.Request;


public class MainApplication extends BaseApp {
    private static final String TAG = BaseApp.class.getSimpleName();

    private Handler handler = new Handler(Looper.getMainLooper());


    public static SharedPreferences sp ;

    @Override
    public void onCreate() {
        L.e(TAG, "-----onCreate------");

        super.onCreate();
        initFile();
        initController();
        initNet();
        initService();
    }

    private void initFile() {
        buildDirectory(Constants.getFileDir());
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


    @SuppressLint("CheckResult")
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

        sp  = BaseApp.inst().getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE);
        String groupName = sp.getString(Constants.getGroupName(), DeviceUtils.getAndroidId(this));
        CurrentVersionReq currentVersionReq = new CurrentVersionReq(AppVersionUtil.getVersionName(BaseApp.inst()),groupName);

        N.API(CallLogApi.class).checkCurrentVersion(currentVersionReq)
                .compose(N.IOMain())
                .subscribe(
                        resp -> {
                            if (resp.code != 0) {
                                Toast.makeText(BaseApp.inst(), resp.data.info, Toast.LENGTH_LONG).show();
                                System.exit(0);
                            }else{
                                if(!resp.data.isExist){
                                    //如果没今日打卡 那就限制使用时长
                                    Toast.makeText(BaseApp.inst(), resp.data.info+"程序将在"+resp.data.timeOut+"分钟后退出！", Toast.LENGTH_LONG).show();
                                    handler.postDelayed(() -> {
                                        System.exit(0);
                                    }, resp.data.timeOut * 60 * 1000);
                                }else{
                                    Toast.makeText(BaseApp.inst(),resp.data.info, Toast.LENGTH_LONG).show();
                                }

                            }
                        },
                        throwable -> {
                            // 网络异常、超时等处理
                            if (throwable instanceof java.net.SocketTimeoutException) {
                                Toast.makeText(BaseApp.inst(), "请求超时，请检查网络", Toast.LENGTH_SHORT).show();
                            } else {
                                L.w(throwable.getMessage());
                                Toast.makeText(BaseApp.inst(), "版本检测失败：" + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                            }

                            // 可选：退出或重试逻辑
                             System.exit(0); // 若失败即需退出，也可保留
                        }
                );



    }


    public void offline() {
        LoginCertificate.clear();
        ActivityManager.finishAllExceptActivity();

// 跳转网页 网页提示 感谢您的使用 下次再见
//        startActivity(new Intent(BaseApp.inst(), LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }


    public void initService() {
//        Context context = BaseApp.inst();
//        CallLogObserver observer = new CallLogObserver(new Handler(),context);
//        context.getContentResolver().registerContentObserver(
//                CallLog.Calls.CONTENT_URI,
//                true,
//                observer
//        );

    }

}



