package com.openim.tophone.utils;

import android.content.Context;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.repository.LocationService;

import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;

public class PhoneLocationHelper {

    public interface LocationCallback {
        void onResult(String location);
        void onError(Throwable e);
    }

    public static void getPhoneLocation(String phoneNumber, String tag, LocationCallback callback) {
        Context context = BaseApp.inst();
        if (context == null) {
            callback.onError(new IllegalStateException("application context unavailable"));
            return;
        }
        String deviceCode = DeviceUtils.getOrCreateClientDeviceId(context);
        if (deviceCode == null || deviceCode.trim().isEmpty()) {
            callback.onError(new IllegalStateException("device code unavailable"));
            return;
        }

        Disposable disposable = N.mAPI(LocationService.class)
                .getPhoneNumberLocation(deviceCode, phoneNumber)
                .timeout(5, TimeUnit.SECONDS)
                .compose(N.IOMain())
                .subscribe(response -> {
                    String display = response == null ? "" : response.getDisplay();
                    if (display.isEmpty()) {
                        callback.onError(new IllegalStateException(
                                response == null ? "empty phone location response" : response.msg
                        ));
                        return;
                    }
                    callback.onResult(display);
                }, callback::onError);

        N.addDispose(tag, disposable);
    }
}
