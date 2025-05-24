package com.openim.tophone.utils;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.CallLog;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.openim.entity.CallLogBean;
import com.openim.tophone.repository.CallLogApi;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class CallLogUtils {

    private static final String TAG = "CallLogUtils";

    /**
     * 获取最新一条通话记录并上传
     */
    public void uploadLatestCallLog() {
        Cursor cursor = null;
        try {
            ContentResolver resolver = BaseApp.inst().getContentResolver();
            String[] projection = {
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
            };

            cursor = resolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    CallLog.Calls.DATE + " DESC"
            );

            if (cursor != null && cursor.moveToFirst()) {
                CallLogBean callLog = parseCallLog(cursor);
                logCallLog(callLog);
                if(TextUtils.isEmpty(callLog.getParentUID())){
                    //如果甲方ID为空 那么就不上传通话日志
                    return ;
                }

                uploadCallLog(callLog);
            } else {
                Log.w(TAG, "通话记录为空或查询失败");
            }

        } catch (Exception e) {
            Log.e(TAG, "读取通话记录异常", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * 解析 Cursor 中的一条通话记录
     */
    private CallLogBean parseCallLog(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls._ID));
        String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
        int type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
        long dateMillis = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
        int duration = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));

        String formattedDate = formatDate(dateMillis);

        return new CallLogBean(id, number, type, formattedDate, duration);
    }

    /**
     * 将时间戳格式化为 ISO 8601 字符串
     */
    @SuppressLint("SimpleDateFormat")
    private String formatDate(long millis) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.CHINA);
        return formatter.format(new Date(millis));
    }

    /**
     * 上传通话记录
     */
    @SuppressLint("CheckResult")
    private void uploadCallLog(CallLogBean callLog) {
        if (callLog == null) return;

        N.API(CallLogApi.class).uploadCallLog(callLog)
                .compose(N.IOMain())
                .subscribe(
                        resp -> {
                            Log.i(TAG, "上传成功: " + resp.msg);
                            Toast.makeText(BaseApp.inst(), "上传成功: " + resp.msg, Toast.LENGTH_SHORT).show();
                        },
                        err -> {
                            Log.e(TAG, "上传失败", err);
                            Toast.makeText(BaseApp.inst(), "上传失败: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                );
    }

    /**
     * 打印日志信息
     */
    private void logCallLog(CallLogBean log) {
        if (log == null) return;
        String info = String.format(
                Locale.getDefault(),
                "通话记录：\nID: %d\n号码: %s\n类型: %s\n日期: %s\n时长: %d秒\n",
                log.getCallID(),
                log.getCallNumber(),
                getCallTypeString(log.getCallType()),
                log.getCallStartAt(),
                log.getCallDuration()
        );
        Log.d(TAG, info);
    }

    /**
     * 将通话类型转换为字符串
     */
    private String getCallTypeString(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE:
                return "来电";
            case CallLog.Calls.OUTGOING_TYPE:
                return "去电";
            case CallLog.Calls.MISSED_TYPE:
                return "未接";
            case CallLog.Calls.REJECTED_TYPE:
                return "已拒绝";
            case CallLog.Calls.BLOCKED_TYPE:
                return "已拦截";
            default:
                return "未知类型(" + type + ")";
        }
    }
}
