package com.openim.tophone.utils;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.StringRes;

/** App-wide Toast wrapper. */
public final class AppToast {
    private AppToast() {
    }

    public static void show(Context context, CharSequence text, int duration) {
        if (context == null) {
            return;
        }
        Toast.makeText(context.getApplicationContext(), text, duration).show();
    }

    public static void show(Context context, @StringRes int resId, int duration) {
        if (context == null) {
            return;
        }
        show(context, context.getString(resId), duration);
    }
}
