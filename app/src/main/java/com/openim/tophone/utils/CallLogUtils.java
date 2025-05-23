package com.openim.tophone.utils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.CallLog;
import android.util.Log;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.net.bage.Base;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CallLogUtils {
    private Cursor cursor;

    public void getLatestCallLogDetails() {
        StringBuilder callLog = new StringBuilder();
        ContentResolver cr = BaseApp.inst().getContentResolver();

        // 定义要查询的字段，包括_ID
        String[] projection = {
                CallLog.Calls._ID,           // 通话记录的唯一ID
                CallLog.Calls.NUMBER,        // 电话号码
                CallLog.Calls.TYPE,          // 通话类型
                CallLog.Calls.DATE,          // 通话日期
                CallLog.Calls.DURATION       // 通话时长
        };

        // 查询通话记录
        Cursor cursor = cr.query(
                CallLog.Calls.CONTENT_URI,
                projection,                  // 指定要返回的字段
                null,                        // 不使用筛选条件
                null,                        // 不使用筛选参数
                CallLog.Calls.DATE + " DESC" // 按日期降序排列
        );

        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 获取各字段的索引
                int idIndex = cursor.getColumnIndex(CallLog.Calls._ID);
                int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
                int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);
                int durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);


                long callId = cursor.getLong(idIndex); // 获取通话记录ID
                String phoneNumber = cursor.getString(numberIndex);
                int callType = cursor.getInt(typeIndex);
                long callDate = cursor.getLong(dateIndex);
                int callDuration = cursor.getInt(durationIndex);

                // 格式化日期
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String dateString = formatter.format(new Date(callDate));

                // 判断通话类型
                String callTypeStr = getCallTypeString(callType);

                // 格式化通话时长
//                    String durationStr = formatDuration(callDuration);

                // 添加通话记录信息到StringBuilder
                callLog.append("ID: ").append(callId).append("\n")
                        .append("号码: ").append(phoneNumber).append("\n")
                        .append("类型: ").append(callTypeStr).append("\n")
                        .append("日期: ").append(dateString).append("\n")
                        .append("时长: ").append(callDuration).append("\n\n");


                // 显示通话记录
                showCallLog(callLog.toString());

            } finally {
                cursor.close();
            }
        }
    }

    // 获取通话类型的字符串表示
    private String getCallTypeString(int callType) {
        switch (callType) {
            case CallLog.Calls.INCOMING_TYPE:
                return "来电";
            case CallLog.Calls.OUTGOING_TYPE:
                return "去电";
            case CallLog.Calls.MISSED_TYPE:
                return "未接";
            case CallLog.Calls.REJECTED_TYPE:
                return "已拒绝";
            case CallLog.Calls.BLOCKED_TYPE: // API 24+
                return "已拦截";
            default:
                return "未知类型(" + callType + ")";
        }
    }

//    // 格式化通话时长
//    private String formatDuration(int seconds) {
//        if (seconds < 60) {
//            return seconds + "秒";
//        } else {
//            int mins = seconds / 60;
//            int secs = seconds % 60;
//            return mins + "分" + secs + "秒";
//        }
//    }

    // 显示通话记录
    private void showCallLog(String callLog) {
        // 这里可以使用TextView或其他UI组件显示通话记录
        Log.d("CallLog", callLog);

    }
}
