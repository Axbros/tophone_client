package com.openim.tophone.rtc;

import android.content.Context;

/**
 * Captures uncaught crashes into {@link RtcDebugLogStore} before the process exits.
 */
public final class RtcCrashHandler implements Thread.UncaughtExceptionHandler {

    private final Thread.UncaughtExceptionHandler defaultHandler;

    private RtcCrashHandler(Thread.UncaughtExceptionHandler defaultHandler) {
        this.defaultHandler = defaultHandler;
    }

    public static void install(Context context) {
        RtcDebugLog.init(context.getApplicationContext());
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof RtcCrashHandler) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new RtcCrashHandler(current));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            RtcDebugLog.e("CRASH", throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            RtcDebugLogStore.writeCrash(thread, throwable);
        } catch (Throwable ignored) {
        }
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }
}
