package com.openim.tophone.utils;

import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.openim.tophone.R;

/**
 * Floating latency badge pinned to the top-left of the current activity window.
 */
public final class ServerPingUi {
    private static final String OVERLAY_TAG = "server_ping_overlay";

    private ServerPingUi() {
    }

    public static void attachWhenReady(Activity activity, Runnable onAttached) {
        if (activity == null || onAttached == null) {
            return;
        }
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> {
            TextView pingView = attach(activity);
            if (pingView != null) {
                onAttached.run();
            }
        });
    }

    @Nullable
    public static TextView attach(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return null;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return null;
        }
        View existing = content.findViewWithTag(OVERLAY_TAG);
        if (existing != null) {
            existing.bringToFront();
            return existing.findViewById(R.id.server_ping_text);
        }
        View overlay = LayoutInflater.from(activity).inflate(R.layout.server_ping_badge, content, false);
        overlay.setTag(OVERLAY_TAG);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        );
        int side = dpToPx(activity, 12);
        lp.setMargins(side, statusBarHeight(activity) + side, side, side);
        content.addView(overlay, lp);
        overlay.bringToFront();
        overlay.setElevation(dpToPx(activity, 12));
        return overlay.findViewById(R.id.server_ping_text);
    }

    private static int statusBarHeight(Activity activity) {
        int resId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            return activity.getResources().getDimensionPixelSize(resId);
        }
        return dpToPx(activity, 24);
    }

    private static int dpToPx(Activity activity, int dp) {
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
