package com.openim.tophone.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.openim.tophone.R;
import com.openim.tophone.base.BaseApp;
public class SmsBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AppToast.show(BaseApp.inst(), R.string.toast_sms_processing, Toast.LENGTH_LONG);
        if (intent != null && "android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (parts == null || parts.length == 0) return;

            String sender = null;
            StringBuilder fullMessage = new StringBuilder();
            for (SmsMessage part : parts) {
                if (part == null) continue;
                if (sender == null || sender.isEmpty()) {
                    sender = part.getOriginatingAddress();
                }
                String bodyPart = part.getMessageBody();
                if (bodyPart != null) {
                    fullMessage.append(bodyPart);
                }
            }

            if (sender != null && sender.length() >= 11 && fullMessage.length() > 0) {
                long deviceTime = System.currentTimeMillis();
                MqttEventUtil.publishSmsReceived(sender, fullMessage.toString(), deviceTime);
                AppToast.show(BaseApp.inst(), R.string.toast_sms_reported, Toast.LENGTH_LONG);
            }
        }
    }
}
