package com.openim.tophone.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.text.TextUtils;
import android.util.Log;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.enums.CallLogType;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.openim.entity.CallLogBean;
import com.openim.tophone.repository.CallLogApi;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


public class CallLogUtils {

    private static final String TAG = "CallLogUtils";
    private static final long CALL_MATCH_TOLERANCE_MS = 10_000L;
    private static final int MAX_CANDIDATES = 10;
    private static final String KEY_LAST_UPLOADED_CALL_LOG_ID = "last_uploaded_call_log_id";
    private static final Set<Long> IN_FLIGHT_IDS =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * 获取最新一条通话记录并上传
     */
    public boolean uploadLatestCallLog(long sessionStartedAt, String expectedNumber) {
        if (ContextCompat.checkSelfPermission(BaseApp.inst(), Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG not granted, skip call log upload");
            return false;
        }
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

            long lowerBound = Math.max(0L, sessionStartedAt - CALL_MATCH_TOLERANCE_MS);
            cursor = resolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    CallLog.Calls.DATE + " >= ?",
                    new String[]{String.valueOf(lowerBound)},
                    CallLog.Calls.DATE + " DESC"
            );

            int inspected = 0;
            while (cursor != null && cursor.moveToNext() && inspected++ < MAX_CANDIDATES) {
                CallLogBean callLog = parseCallLog(cursor);
                if (!phoneNumbersMatch(expectedNumber, callLog.getCallNumber())) {
                    continue;
                }
                logCallLog(callLog);
                if (TextUtils.isEmpty(callLog.getParentUID())) {
                    Log.i(TAG, "group owner is empty, keep system call log for a later upload");
                    return true;
                }
                long lastUploadedID = SharedPreferencesUtil.get(BaseApp.inst())
                        .getLong(KEY_LAST_UPLOADED_CALL_LOG_ID);
                if (lastUploadedID == callLog.getCallID()) {
                    deleteCallLogByID(callLog.getCallID());
                    return true;
                }
                uploadCallLog(callLog);
                return true;
            }
            Log.w(TAG, "no call log matched current call session");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "读取通话记录异常", e);
            return false;
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
        if (!IN_FLIGHT_IDS.add(callLog.getCallID())) {
            Log.d(TAG, "call log upload already in flight: " + callLog.getCallID());
            return;
        }

        N.API(CallLogApi.class).uploadCallLog(callLog)
                .compose(N.IOMain())
                .retry(2)
                .doFinally(() -> IN_FLIGHT_IDS.remove(callLog.getCallID()))
                .subscribe(
                        resp -> {
                            if (resp == null || resp.code != 0) {
                                Log.w(TAG, "upload rejected: " + (resp == null ? "empty response" : resp.msg));
                                return;
                            }
                            Log.i(TAG, "上传成功: " + resp.msg);
                            SharedPreferencesUtil prefs = SharedPreferencesUtil.get(BaseApp.inst());
                            prefs.setCache(KEY_LAST_UPLOADED_CALL_LOG_ID, callLog.getCallID());
                            recordLocalStatistic(prefs, callLog.getCallType());
                            MqttEventUtil.publishCallRecord(callLog);
                            deleteCallLogByID(callLog.getCallID());
                        },
                        err -> {
                            Log.e(TAG, "上传失败", err);
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

    public void deleteCallLogByID(long callLogID) {
        if (ContextCompat.checkSelfPermission(BaseApp.inst(), Manifest.permission.WRITE_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "WRITE_CALL_LOG not granted, uploaded record cannot be removed: " + callLogID);
            return;
        }
        Uri callLogUri = CallLog.Calls.CONTENT_URI;
        String where = CallLog.Calls._ID + " = ?";
        String[] args = new String[]{String.valueOf(callLogID)};
        try {
            int rowsDeleted = BaseApp.inst().getContentResolver().delete(callLogUri, where, args);
            Log.d(TAG, "deleted uploaded call log id=" + callLogID + ", rows=" + rowsDeleted);
        } catch (SecurityException e) {
            Log.e(TAG, "delete uploaded call log denied: " + callLogID, e);
        }
    }

    private void recordLocalStatistic(SharedPreferencesUtil prefs, int type) {
        if (type == CallLog.Calls.OUTGOING_TYPE) {
            prefs.recordCallEvent(CallLogType.CALL_OUT.getDescription());
            return;
        }
        if (type == CallLog.Calls.INCOMING_TYPE
                || type == CallLog.Calls.MISSED_TYPE
                || type == CallLog.Calls.REJECTED_TYPE
                || type == CallLog.Calls.BLOCKED_TYPE) {
            prefs.recordCallEvent(CallLogType.CALL_IN.getDescription());
        }
    }

    private boolean phoneNumbersMatch(String expected, String actual) {
        String expectedDigits = digitsOnly(expected);
        if (expectedDigits.isEmpty()) {
            return true;
        }
        String actualDigits = digitsOnly(actual);
        if (actualDigits.isEmpty()) {
            return false;
        }
        if (expectedDigits.equals(actualDigits)) {
            return true;
        }
        int suffixLength = Math.min(7, Math.min(expectedDigits.length(), actualDigits.length()));
        return suffixLength >= 7
                && expectedDigits.substring(expectedDigits.length() - suffixLength)
                .equals(actualDigits.substring(actualDigits.length() - suffixLength));
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

}
