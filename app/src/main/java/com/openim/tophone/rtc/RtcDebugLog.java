package com.openim.tophone.rtc;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Mirrors RTC audio routing logs to UI and persists them to disk for remote debugging. */
public final class RtcDebugLog {

    private static final int MAX_LINES = 120;
    private static final String TAG = "RtcDebugLog";

    public interface Sink {
        void onLogUpdated(String fullText);
    }

    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    @Nullable
    private static volatile Sink sink;
    private static boolean initialized;

    private RtcDebugLog() {
    }

    public static void init(Context context) {
        if (initialized) {
            return;
        }
        RtcDebugLogStore.init(context.getApplicationContext());
        List<String> restored = RtcDebugLogStore.readRecentLogLines(MAX_LINES);
        synchronized (lines) {
            lines.clear();
            for (String line : restored) {
                lines.addLast(line);
            }
        }
        initialized = true;
    }

    public static void setSink(@Nullable Sink sink) {
        RtcDebugLog.sink = sink;
        if (sink != null) {
            sink.onLogUpdated(getDisplayText());
        }
    }

    public static void clear() {
        synchronized (lines) {
            lines.clear();
        }
        RtcDebugLogStore.clearAll();
        notifySink();
    }

    /** Text shown in the debug panel (includes last crash if any). */
    public static String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        if (RtcDebugLogStore.hasCrashReport()) {
            sb.append("===== LAST CRASH (copy & send) =====\n");
            sb.append(RtcDebugLogStore.readCrashReport());
            sb.append("\n===== LOG =====\n");
        }
        synchronized (lines) {
            for (String line : lines) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /** Full export including complete log file on disk. */
    public static String getExportText() {
        return RtcDebugLogStore.readExportText();
    }

    public static boolean hasCrashReport() {
        return RtcDebugLogStore.hasCrashReport();
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
        append(tag, message);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
        append(tag, "WARN " + message);
    }

    public static void e(String tag, String message) {
        Log.e(tag, message);
        append(tag, "ERROR " + message);
    }

    private static void append(String tag, String message) {
        String line = TIME_FMT.format(new Date()) + " [" + tag + "] " + message;
        synchronized (lines) {
            lines.addLast(line);
            while (lines.size() > MAX_LINES) {
                lines.removeFirst();
            }
        }
        RtcDebugLogStore.appendLine(line);
        notifySink();
    }

    private static void notifySink() {
        Sink current = sink;
        if (current != null) {
            current.onLogUpdated(getDisplayText());
        }
    }
}
