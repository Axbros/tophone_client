package com.openim.tophone.repository;

import com.openim.tophone.openim.entity.CallLogBean;
import com.openim.tophone.openim.entity.UploadCallLogResp;

import io.reactivex.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface CallLogApi {
    @POST("http://192.168.31.27:8080/api/v2/call_log/ ")
//    @POST("https://api.ndvfp.cn/callLogUpload ")
    Observable<UploadCallLogResp> uploadCallLog(@Body CallLogBean callLog); // Base 是你的接口返回数据模型
}
