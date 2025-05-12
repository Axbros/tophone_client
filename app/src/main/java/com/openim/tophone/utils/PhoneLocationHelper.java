package com.openim.tophone.utils;

import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.repository.LocationService;

import io.reactivex.disposables.Disposable;

public class PhoneLocationHelper {

    public interface LocationCallback {
        void onResult(String location);
        void onError(Throwable e);
    }

    private static final String ID = "10004275";
    private static final String KEY = "819bb34ae3ff372bae58d900877443d5";

    public static void getPhoneLocation(String phoneNumber, String tag, LocationCallback callback) {
        Disposable disposable = N.API(LocationService.class)
                .getPhoneNumberLocation(ID, KEY, phoneNumber)
                .compose(N.IOMain()) // ⬅️ 切换线程：IO请求 + 主线程回调
                .subscribe(response -> {
                    if (response.code == 200) {
                        String location = response.shengfen + "·" + response.chengshi + "·" + response.fuwushang;
                        callback.onResult(location);
                    } else {
                        callback.onResult("中國·大陸");
                    }
                }, throwable -> {
                    callback.onError(throwable);
                });

        // ⬅️ 建议添加 Disposable 管理，避免内存泄漏
        N.addDispose(tag, disposable);
    }
}
