package com.openim.tophone.utils;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.StringRes;

/** App-wide Toast wrapper with a cloak-screen gate. */
public final class AppToast {
    private static final Object LOCK = new Object();
    private static volatile boolean cloakVisible;
    private static Toast activeToast;

    private AppToast() {
    }

    public static void setCloakVisible(boolean visible) {
        cloakVisible = visible;
        if (!visible) {
            return;
        }
        synchronized (LOCK) {
            if (activeToast != null) {
                activeToast.cancel();
                activeToast = null;
            }
        }
    }

    public static void show(Context context, CharSequence text, int duration) {
        if (cloakVisible || context == null) {
            return;
        }
        Toast toast = Toast.makeText(context.getApplicationContext(), text, duration);
        synchronized (LOCK) {
            if (cloakVisible) {
                return;
            }
            activeToast = toast;
            toast.show();
        }
    }

    public static void show(Context context, @StringRes int resId, int duration) {
        if (context == null) {
            return;
        }
        show(context, context.getString(resId), duration);
    }
}
