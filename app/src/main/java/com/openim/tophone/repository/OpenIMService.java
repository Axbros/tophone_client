package com.openim.tophone.repository;

import com.openim.tophone.net.RXRetrofit.Exception.RXRetrofitException;
import com.openim.tophone.net.bage.Base;
import com.openim.tophone.net.bage.GsonHel;
import io.reactivex.Observable;
import io.reactivex.functions.Function;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import retrofit2.http.Body;

import retrofit2.http.POST;

public interface OpenIMService {
    @POST("account/login")
    Observable<ResponseBody> login(@Body RequestBody requestBody);

    @POST("account/register")
    Observable<ResponseBody> register(@Body RequestBody requestBody);

    static <T> Function<ResponseBody, T> turn(Class<T> tClass) {
        return responseBody -> {
            String body = responseBody.string();
            Base<T> base = GsonHel.dataObject(body, tClass);
            if (base.errCode == 0)
                return null == base.data ? tClass.newInstance() : base.data;
            throw new RXRetrofitException(base.errCode, base.errDlt);
        };
    }
}
