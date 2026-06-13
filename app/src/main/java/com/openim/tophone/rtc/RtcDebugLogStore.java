package com.openim.tophone.rtc;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Persists RTC debug logs and last crash to disk so they survive process death. */
final class RtcDebugLogStore {

    private static final String DIR_NAME = "rtc_debug";
    private static final String LOG_FILE = "rtc_audio.log";
    private static final String CRASH_FILE = "rtc_last_crash.txt";
    private static final int MAX_LOG_BYTES = 256 * 1024;
    private static final SimpleDateFormat FILE_TIME_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static File logFile;
    private static File crashFile;
    private static final Object lock = new Object();

    private RtcDebugLogStore() {
    }

    static void init(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        logFile = new File(dir, LOG_FILE);
        crashFile = new File(dir, CRASH_FILE);
    }

    static void appendLine(String line) {
        if (logFile == null) {
            return;
        }
        synchronized (lock) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(line);
                writer.write('\n');
                writer.flush();
            } catch (IOException ignored) {
            }
            trimIfNeeded();
        }
    }

    static void writeCrash(Thread thread, Throwable error) {
        if (crashFile == null) {
            return;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("time=" + FILE_TIME_FMT.format(new Date()));
        pw.println("thread=" + (thread != null ? thread.getName() : "?"));
        pw.println("exception=" + error);
        error.printStackTrace(pw);
        pw.flush();
        synchronized (lock) {
            try (FileWriter writer = new FileWriter(crashFile, false)) {
                writer.write(sw.toString());
                writer.flush();
            } catch (IOException ignored) {
            }
            appendLine(FILE_TIME_FMT.format(new Date()) + " [CRASH] uncaught on thread "
                    + (thread != null ? thread.getName() : "?") + ": " + error);
        }
    }

    static boolean hasCrashReport() {
        return crashFile != null && crashFile.exists() && crashFile.length() > 0;
    }

    static String readCrashReport() {
        return readFile(crashFile);
    }

    static List<String> readRecentLogLines(int maxLines) {
        List<String> all = readAllLogLines();
        if (all.size() <= maxLines) {
            return all;
        }
        return all.subList(all.size() - maxLines, all.size());
    }

    static String readExportText() {
        StringBuilder sb = new StringBuilder();
        if (hasCrashReport()) {
            sb.append("===== LAST CRASH =====\n");
            sb.append(readCrashReport());
            if (!sb.toString().endsWith("\n")) {
                sb.append('\n');
            }
            sb.append("===== END CRASH =====\n\n");
        }
        sb.append("===== RTC AUDIO LOG =====\n");
        sb.append(readFile(logFile));
        return sb.toString();
    }

    static void clearAll() {
        synchronized (lock) {
            deleteQuietly(logFile);
            deleteQuietly(crashFile);
        }
    }

    private static void trimIfNeeded() {
        if (logFile == null || !logFile.exists() || logFile.length() <= MAX_LOG_BYTES) {
            return;
        }
        List<String> lines = readAllLogLines();
        int keepFrom = Math.max(0, lines.size() - 400);
        try (FileWriter writer = new FileWriter(logFile, false)) {
            for (int i = keepFrom; i < lines.size(); i++) {
                writer.write(lines.get(i));
                writer.write('\n');
            }
            writer.flush();
        } catch (IOException ignored) {
        }
    }

    private static List<String> readAllLogLines() {
        List<String> lines = new ArrayList<>();
        if (logFile == null || !logFile.exists()) {
            return lines;
        }
        synchronized (lock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException ignored) {
            }
        }
        return lines;
    }

    private static String readFile(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        synchronized (lock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            } catch (IOException e) {
                return "";
            }
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}
