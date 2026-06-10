package com.openim.tophone.repository;

import com.openim.tophone.openim.entity.DeviceLoginReq;
import com.openim.tophone.openim.entity.DeviceLoginResp;

import io.reactivex.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface LoginApi {
    @POST("api/v1/login/device")
    Observable<DeviceLoginResp> deviceLogin(@Body DeviceLoginReq req);
}
