package com.openim.tophone.utils;

import android.app.Activity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.openim.tophone.R;

/**
 * Binds server latency to an in-layout badge when present, otherwise a floating top-left overlay.
 */
public final class ServerPingUi {
    private static final String TAG = "ServerPing";
    private static final String OVERLAY_TAG = "server_ping_overlay";

    private ServerPingUi() {
    }

    public static void bindWhenReady(Activity activity, Runnable onBound) {
        if (activity == null || onBound == null) {
            return;
        }
        activity.getWindow().getDecorView().post(() -> {
            TextView pingView = bind(activity);
            if (pingView != null) {
                Log.i(TAG, "bindWhenReady: ok activity=" + activity.getClass().getSimpleName());
                onBound.run();
            } else {
                Log.w(TAG, "bindWhenReady: failed activity=" + activity.getClass().getSimpleName());
            }
        });
    }

    @Nullable
    public static TextView bind(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return null;
        }
        TextView embedded = activity.findViewById(R.id.server_ping_text);
        if (embedded != null) {
            Log.d(TAG, "bind: embedded badge activity=" + activity.getClass().getSimpleName());
            View badge = (View) embedded.getParent();
            if (badge != null) {
                badge.setVisibility(View.VISIBLE);
            }
            return embedded;
        }
        Log.d(TAG, "bind: floating overlay activity=" + activity.getClass().getSimpleName());
        return attachFloatingOverlay(activity);
    }

    @Nullable
    private static TextView attachFloatingOverlay(Activity activity) {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        View existing = decor.findViewWithTag(OVERLAY_TAG);
        if (existing != null) {
            existing.bringToFront();
            existing.setVisibility(View.VISIBLE);
            return existing.findViewById(R.id.server_ping_text);
        }
        View overlay = LayoutInflater.from(activity).inflate(R.layout.server_ping_badge, decor, false);
        overlay.setTag(OVERLAY_TAG);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        );
        int side = dpToPx(activity, 12);
        lp.setMargins(side, statusBarHeight(activity) + side, side, side);
        decor.addView(overlay, lp);
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
