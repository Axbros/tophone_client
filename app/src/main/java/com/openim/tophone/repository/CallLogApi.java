package com.openim.tophone.repository;

import io.reactivex.Observable;

import com.openim.tophone.openim.entity.CallLogBean;
import com.openim.tophone.openim.entity.CheckVersionResp;
import com.openim.tophone.openim.entity.UploadCallLogResp;
import com.openim.tophone.openim.entity.CurrentVersionReq;

import retrofit2.http.Body;
import retrofit2.http.POST;

public interface CallLogApi {
    @POST("api/v1/call_log/")
    Observable<UploadCallLogResp> uploadCallLog(@Body CallLogBean callLog); // Base 是你的接口返回数据模型

    @POST("api/v1/call_log/check_version")
    Observable<CheckVersionResp> checkCurrentVersion(@Body CurrentVersionReq currentVersionReq);
}
