package com.openim.tophone.rtc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

public class RtcToastUtil {

    private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());
    private static Toast toast;
    private static AlertDialog dialog;

    public static void showAlert(Context context, String message) {
        UI_HANDLER.post(() -> {
            if (dialog != null && dialog.isShowing()) {
                return;
            }
            dialog = new AlertDialog.Builder(context).setTitle("错误").setMessage(message)
                    .setPositiveButton("OK", (d, which) -> d.dismiss())
                    .show();
        });
    }

    public static void showLongToast(Context context, final String msg) {
        UI_HANDLER.post(() -> {
            if (toast != null) {
                toast.cancel();
            }
            toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
            toast.show();
        });
    }

    public static void showShortToast(Context context, final String msg) {
        UI_HANDLER.post(() -> {
            if (toast != null) {
                toast.cancel();
            }
            toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
            toast.show();
        });
    }
}
