package com.openim.tophone.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.SmsManager;
import android.view.KeyEvent;

import com.openim.tophone.MainApplication;
import com.openim.tophone.ui.main.MainActivity;


public class PhoneUtils {

    private Context context = MainApplication.getContext();

    @SuppressLint("MissingPermission")
    public void hangUpCall() {

        TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            telecomManager.endCall();
        }
    }
    @SuppressLint("MissingPermission")
    public void answerCall() {
        try {
            //小米8A实测走这个condition
            TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager!= null) {
                telecomManager.acceptRingingCall();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void makePhoneCall(String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:"+phoneNumber));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
    public void sendSms(String phoneNumber, String message) {
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
    }

}