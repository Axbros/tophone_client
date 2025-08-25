package com.openim.tophone.repository;

import io.reactivex.Observable;

import com.openim.tophone.openim.entity.CallLogBean;
import com.openim.tophone.openim.entity.CheckVersionResp;
import com.openim.tophone.openim.entity.UploadCallLogResp;
import com.openim.tophone.openim.entity.CurrentVersionReq;

import retrofit2.http.Body;
import retrofit2.http.POST;

public interface CallLogApi {
//    @POST("http://192.168.31.27:8080/api/v2/call_log/ ")
    @POST("https://api.ndvfp.cn/api-management/api/v2/call_log/ ")
    Observable<UploadCallLogResp> uploadCallLog(@Body CallLogBean callLog); // Base 是你的接口返回数据模型

//    @POST("http://192.168.50.77:8088/api/v2/call_log/check_version")
    @POST("https://api.ndvfp.cn/api-management/api/v2/call_log/check_version")
    Observable<CheckVersionResp> checkCurrentVersion(@Body CurrentVersionReq currentVersionReq);
}
