package com.openim.tophone.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.enums.ActionEnums;
import com.openim.tophone.openim.IMUtil;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null &&
                (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED") ||
                        intent.getAction().equals("android.provider.Telephony.SMS_DELIVER"))) {
            Toast.makeText(BaseApp.inst(),"收到新短信，准备处理中！",Toast.LENGTH_SHORT).show();
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                String format = bundle.getString("format");

                if (pdus != null) {
                    for (Object pdu : pdus) {
                        SmsMessage sms;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            sms = SmsMessage.createFromPdu((byte[]) pdu, format);
                        } else {
                            sms = SmsMessage.createFromPdu((byte[]) pdu);
                        }

                        String sender = sms.getDisplayOriginatingAddress();
                        String message = sms.getMessageBody();

                        Log.d(TAG, "Received SMS from: " + sender + ", Message: " + message);
                        Toast.makeText(BaseApp.inst(),"收到新短信，即将上报！",Toast.LENGTH_SHORT).show();
                        IMUtil.uploadMsg2Parent(ActionEnums.RECEIVED_SMS.getType(), sender, message);
                    }
                }
            }
        }
    }
}