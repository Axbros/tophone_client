package com.openim.tophone.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.openim.tophone.R;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.utils.MqttEventUtil;

public class SmsBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(BaseApp.inst(), R.string.toast_sms_processing, Toast.LENGTH_LONG).show();
        if (intent != null && "android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle == null) return;

            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null || pdus.length == 0) return;

            for (Object pdu : pdus) {
                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu, bundle.getString("format"));
                if (smsMessage == null) continue;

                String sender = smsMessage.getOriginatingAddress();
                String messageBody = smsMessage.getMessageBody();

                if (sender != null && sender.length() >= 11) {
                    long deviceTime = System.currentTimeMillis();
                    MqttEventUtil.publishSmsReceived(sender, messageBody, deviceTime);
                    Toast.makeText(BaseApp.inst(), R.string.toast_sms_reported, Toast.LENGTH_LONG).show();
                    break; // 处理一条后就退出
                }
            }
        }
    }
}
