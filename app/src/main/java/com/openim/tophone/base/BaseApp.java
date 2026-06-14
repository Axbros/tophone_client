package com.openim.tophone.base;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.widget.TextView;

import com.openim.tophone.base.vm.injection.Easy;
import com.openim.tophone.utils.ServerPingMonitor;
import com.openim.tophone.utils.ServerPingUi;

public class BaseApp extends Application {
    public State<Boolean> isAppBackground = new State<>(true);
    private static BaseApp instance;
    private int mActivityCount;

    public static BaseApp inst() {
        return instance;
    }

    public <T extends BaseViewModel> void putVM(T vm) {
        Easy.put(vm);
    }

    public void removeCacheVM(Class<? extends BaseViewModel> cl) {
        try {
            BaseViewModel vm = Easy.find(cl);
            vm.releaseRes();
        } catch (Exception ignored) {}
        Easy.delete(cl);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        activityLifecycleCallback();
    }

    private void activityLifecycleCallback() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
                mActivityCount++;
                if (isAppBackground.val())
                    isAppBackground.setValue(false);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                ServerPingUi.bindWhenReady(activity, () -> {
                    TextView pingView = ServerPingUi.bind(activity);
                    ServerPingMonitor monitor = ServerPingMonitor.getInstance();
                    monitor.bind(pingView);
                    monitor.start();
                });
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
                mActivityCount--;
                if (mActivityCount == 0) {
                    isAppBackground.setValue(true);
                    ServerPingMonitor.getInstance().stop();
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }
}
