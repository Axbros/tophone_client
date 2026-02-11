package com.openim.tophone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.base.vm.injection.Easy;
import com.openim.tophone.net.RXRetrofit.HttpConfig;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.openim.entity.CurrentVersionReq;
import com.openim.tophone.openim.entity.LoginCertificate;
import com.openim.tophone.openim.vm.UserLogic;
import com.openim.tophone.repository.CallLogApi;
import com.openim.tophone.ui.main.MainActivity;
import com.openim.tophone.utils.ActivityManager;
import com.openim.tophone.utils.AppVersionUtil;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DeviceUtils;
import com.openim.tophone.utils.DomainManager;
import com.openim.tophone.utils.L;

import java.io.File;

import io.openim.android.sdk.BuildConfig;
import okhttp3.Request;

public class MainApplication extends BaseApp {
    private static final String TAG = "VersionCheck";

    // 主线程 Handler（统一用这个）
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static SharedPreferences sp;

    @Override
    public void onCreate() {
        super.onCreate();
        L.e(TAG, "-----onCreate------ pid=" + android.os.Process.myPid());

        initFile();
        initController();

        // 启动先用缓存覆盖默认 host
        String cached = DomainManager.getHost(this);
        if (cached != null && !cached.isEmpty()) {
            Constants.updateHost(cached);
        }

        initNet();
        initService();
    }

    private void initFile() {
        buildDirectory(Constants.getFileDir());
    }

    private boolean buildDirectory(String path) {
        File file = new File(path);
        if (file.exists()) return true;
        return file.mkdirs();
    }

    private void initController() {
        Easy.installVM(UserLogic.class);
    }

    @SuppressLint("CheckResult")
    public void initNet() {
        Log.d(TAG, "initNet called, baseUrl=" + Constants.getAppAuthUrl());

        // 初始化网络
        N.init(new HttpConfig()
                .setBaseUrl(Constants.getAppAuthUrl())
                .setDebug(BuildConfig.DEBUG)
                .addInterceptor(chain -> {
                    String token = "";
                    try {
                        if (BaseApp.inst() != null && BaseApp.inst().loginCertificate != null) {
                            token = BaseApp.inst().loginCertificate.chatToken;
                        }
                    } catch (Exception ignored) {}

                    Request request = chain.request().newBuilder()
                            .addHeader("token", token == null ? "" : token)
                            .addHeader("operationID", String.valueOf(System.currentTimeMillis()))
                            .build();

                    // 调试：确认 interceptor 真的走了（请求确实发出）
                    Log.d(TAG, "HTTP -> " + request.method() + " " + request.url());
                    return chain.proceed(request);
                })
        );

        // 10秒后检查（你原来就是 5*1000*60）
        long delayMs = 1L * 1000L;
        Log.d(TAG, "schedule version check after(ms)=" + delayMs);

        mainHandler.postDelayed(() -> {
            Log.d(TAG, "postDelayed fired -> start checkVersion");
            checkVersionAndLimit();
        }, delayMs);
    }

    @SuppressLint("CheckResult")
    private void checkVersionAndLimit() {
        Context context = BaseApp.inst();
        if (context == null) {
            Log.e(TAG, "context is null, abort");
            return;
        }

        sp = context.getSharedPreferences(
                Constants.getSharedPrefsKeys_FILE_NAME(),
                Context.MODE_PRIVATE
        );

        String groupName = sp.getString(Constants.getGroupName(), DeviceUtils.getAndroidId(context));
        CurrentVersionReq req = new CurrentVersionReq(
                AppVersionUtil.getVersionName(context),
                groupName
        );

        Log.d(TAG, "req.version=" + AppVersionUtil.getVersionName(context) + ", groupName=" + groupName);

        N.mAPI(CallLogApi.class)
                .checkCurrentVersion(req)
                .doOnSubscribe(d -> Log.d(TAG, "subscribed"))
                .doOnError(e -> Log.e(TAG, "doOnError", e))
                .compose(N.IOMain())
                .subscribe(
                        resp -> {
                            Log.d(TAG, "onNext entered, resp=" + safeToString(resp));

                            // 防御：resp 或 data 为空
                            if (resp == null || resp.data == null) {
                                toast(context, "版本检测返回异常，程序即将退出！");
                                forceExit();
                                return;
                            }

                            // ❶ 版本不匹配/被禁用等：立即提示 + 退出
                            if (resp.code != 0) {
                                toast(context, resp.data.info);
                                forceExit();
                                return;
                            }

                            // ❷ 今日未打卡：限时后退出
                            if (!resp.data.isExist) {
                                long timeoutMinutes = Math.max(1, resp.data.timeOut);
                                long timeoutMs = timeoutMinutes * 60L * 1000L;

                                toast(context, resp.data.info + "，程序将在 " + timeoutMinutes + " 分钟后退出！");
                                Log.d(TAG, "not checked-in, schedule exit after(ms)=" + timeoutMs);

                                mainHandler.postDelayed(this::forceExit, timeoutMs);
                                return;
                            }

                            // ❸ 一切正常
                            toast(context, resp.data.info);
                        },
                        throwable -> {
                            Log.e(TAG, "onError entered", throwable);

                            if (throwable instanceof java.net.SocketTimeoutException) {
                                toast(context, "请求超时，请检查网络");
                            } else {
                                toast(context, "版本检测失败：" + (throwable == null ? "unknown" : throwable.getMessage()));
                            }

                            toast(context, "网络异常，程序即将退出！");
                            forceExit();
                        }
                );
    }

    private void toast(Context context, String msg) {
        if (context == null) return;
        if (msg == null) msg = "";
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    /**
     * 统一退出/禁用入口：你想“只禁用按钮”或“直接退出APP”都在这里控制
     */
    private void forceExit() {
        Log.e(TAG, "forceExit called");

        // 你的原逻辑：禁用/断开
        MainActivity.seBtnConnectDisable();
        System.exit(0);

        // 如果你要真正退出 APP：
        // 1) 推荐：结束任务栈（更优雅）
        // Activity top = ActivityManager.getTopActivity(); // 如果你有这个方法
        // if (top != null) top.finishAffinity();

        // 2) 不推荐但简单粗暴：直接杀进程（调试阶段可用）
        // android.os.Process.killProcess(android.os.Process.myPid());
        // System.exit(0);
    }

    private String safeToString(Object o) {
        try {
            return String.valueOf(o);
        } catch (Exception e) {
            return "toString_failed";
        }
    }

    public void offline() {
        LoginCertificate.clear();
        ActivityManager.finishAllExceptActivity();
    }

    public void initService() {
        // 你的 service 初始化保持不变
    }
}