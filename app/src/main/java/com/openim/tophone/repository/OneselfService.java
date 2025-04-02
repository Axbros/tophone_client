package com.openim.tophone.repository;

import java.util.HashMap;

import com.openim.tophone.net.RXRetrofit.Exception.RXRetrofitException;
import com.openim.tophone.net.RXRetrofit.Parameter;
import com.openim.tophone.net.bage.Base;
import com.openim.tophone.net.bage.GsonHel;
import io.reactivex.Observable;
import io.reactivex.functions.Function;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Url;

public interface OneselfService {
    static <T> Function<ResponseBody, T> turn(Class<T> tClass) {
        return responseBody -> {
            String body = responseBody.string();
            Base<T> base = GsonHel.dataObject(body, tClass);
            if (base.errCode == 0) return null == base.data ? tClass.newInstance() : base.data;
            throw new RXRetrofitException(base.errCode, base.errDlt);
        };
    }

    static Parameter buildPagination(int pageNumber, int showNumber) {
        HashMap<String, Integer> pagination = new HashMap<>();
        pagination.put("pageNumber", pageNumber);
        pagination.put("showNumber", showNumber);
        return new Parameter().add("pagination", pagination);
    }

    @POST
    Observable<ResponseBody> getUsersOnlineStatus(@Url String url, @Header("token") String token,
                                                  @Body RequestBody requestBody);



    @POST("user/search/full")
    Observable<ResponseBody> searchUser(@Body RequestBody requestBody);

    @POST("user/find/full")
    Observable<ResponseBody> getUsersFullInfo(@Body RequestBody requestBody);


}
