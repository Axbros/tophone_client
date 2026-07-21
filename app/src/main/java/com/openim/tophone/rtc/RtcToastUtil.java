package com.openim.tophone.rtc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;

import com.openim.tophone.R;
import com.openim.tophone.utils.AppToast;

public class RtcToastUtil {

    private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());
    private static AlertDialog dialog;

    public static void showAlert(Context context, String message) {
        UI_HANDLER.post(() -> {
            if (dialog != null && dialog.isShowing()) {
                return;
            }
            dialog = new AlertDialog.Builder(context).setTitle(R.string.rtc_error_title).setMessage(message)
                    .setPositiveButton("OK", (d, which) -> d.dismiss())
                    .show();
        });
    }

    public static void showLongToast(Context context, final String msg) {
        UI_HANDLER.post(() -> {
            AppToast.show(context, msg, android.widget.Toast.LENGTH_LONG);
        });
    }

    public static void showShortToast(Context context, final String msg) {
        UI_HANDLER.post(() -> {
            AppToast.show(context, msg, android.widget.Toast.LENGTH_SHORT);
        });
    }
}
