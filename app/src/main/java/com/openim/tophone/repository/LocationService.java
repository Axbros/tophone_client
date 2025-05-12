package com.openim.tophone.repository;


import com.openim.tophone.openim.entity.PhoneLocationResponse;
import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface LocationService {
    @GET("https://cn.apihz.cn/api/ip/shouji.php")
    Observable<PhoneLocationResponse> getPhoneNumberLocation(
            @Query("id") String id,
            @Query("key") String key,
            @Query("phone") String phoneNumber
    );
}
