package com.openim.tophone.repository;

import com.openim.tophone.openim.entity.PhoneLocationResponse;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface LocationService {
    @GET("api/v1/phone/location")
    Observable<PhoneLocationResponse> getPhoneNumberLocation(
            @Header("X-Device-Code") String deviceCode,
            @Query("phone") String phoneNumber
    );
}
