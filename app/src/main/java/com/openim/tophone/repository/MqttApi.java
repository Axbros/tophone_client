package com.openim.tophone.repository;

import com.openim.tophone.openim.entity.ApiResp;
import com.openim.tophone.openim.entity.DevicePresenceReq;
import com.openim.tophone.openim.entity.MqttDeviceTokenReq;
import com.openim.tophone.openim.entity.MqttTokenResp;

import io.reactivex.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface MqttApi {
    @POST("api/v1/mqtt/token/device")
    Observable<MqttTokenResp> fetchDeviceToken(@Body MqttDeviceTokenReq req);

    @POST("api/v1/mqtt/device/presence")
    Observable<ApiResp> reportPresence(@Body DevicePresenceReq req);
}
