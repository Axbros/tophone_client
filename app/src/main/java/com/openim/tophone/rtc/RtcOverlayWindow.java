package com.openim.tophone.rtc;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.openim.tophone.R;

/**
 * Small draggable overlay shown on top of the system phone call UI while RTC is in room.
 * Requires {@link Settings#canDrawOverlays(Context)} (SYSTEM_ALERT_WINDOW).
 */
public final class RtcOverlayWindow {

    private static final int WINDOW_SIZE_DP = 56;

    private final Context appContext;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams windowParams;
    private final FrameLayout floatView;
    private boolean isShowing;
    private boolean autoShown;
    private float initialTouchX;
    private float initialTouchY;
    private float initialWindowX;
    private float initialWindowY;

    public RtcOverlayWindow(Context context) {
        appContext = context.getApplicationContext();
        windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = appContext.getResources().getDisplayMetrics();
        int sizePx = (int) (WINDOW_SIZE_DP * metrics.density + 0.5f);

        windowParams = new WindowManager.LayoutParams(
                sizePx,
                sizePx,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        windowParams.gravity = Gravity.TOP | Gravity.START;
        windowParams.x = metrics.widthPixels - sizePx - (int) (16 * metrics.density);
        windowParams.y = (int) (120 * metrics.density);

        floatView = new FrameLayout(appContext);
        ImageView icon = new ImageView(appContext);
        icon.setImageResource(R.drawable.icon_taiji);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        floatView.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        floatView.setBackgroundResource(R.drawable.rtc_card_bg);
        floatView.setOnClickListener(v -> openRtcActivity());
        floatView.setOnTouchListener(new DragTouchListener());
    }

    public static boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return Settings.canDrawOverlays(context.getApplicationContext());
    }

    /** Show overlay during an active phone call while RTC session is in room. */
    public boolean showForPhoneCall() {
        if (!canDrawOverlays(appContext)) {
            return false;
        }
        autoShown = true;
        return showInternal();
    }

    public boolean showManual() {
        if (!canDrawOverlays(appContext)) {
            return false;
        }
        autoShown = false;
        return showInternal();
    }

    public void hideIfAutoShown() {
        if (isShowing && autoShown) {
            hideInternal();
        }
    }

    public void hide() {
        hideInternal();
    }

    public boolean isShowing() {
        return isShowing;
    }

    private boolean showInternal() {
        if (isShowing) {
            return true;
        }
        try {
            windowManager.addView(floatView, windowParams);
            isShowing = true;
            return true;
        } catch (Exception e) {
            isShowing = false;
            RtcDebugLog.e("RtcOverlay", "showInternal failed: " + e);
            return false;
        }
    }

    private void hideInternal() {
        if (!isShowing) {
            return;
        }
        try {
            windowManager.removeView(floatView);
        } catch (Exception ignored) {
        }
        isShowing = false;
        autoShown = false;
    }

    private void openRtcActivity() {
        Intent intent = new Intent(appContext, RawAudioDataActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        appContext.startActivity(intent);
    }

    private final class DragTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    initialWindowX = windowParams.x;
                    initialWindowY = windowParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    windowParams.x = (int) (initialWindowX + event.getRawX() - initialTouchX);
                    windowParams.y = (int) (initialWindowY + event.getRawY() - initialTouchY);
                    if (isShowing) {
                        windowManager.updateViewLayout(floatView, windowParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = Math.abs(event.getRawX() - initialTouchX);
                    float dy = Math.abs(event.getRawY() - initialTouchY);
                    if (dx < 10 && dy < 10) {
                        v.performClick();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
}
