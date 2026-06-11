package com.openim.tophone.rtc;

import android.util.SparseArray;

public final class NetworkQualityText {
    private NetworkQualityText() {}

    private static final SparseArray<String> MAP = new SparseArray<>();

    static {
        MAP.put(0, "Unknown");
        MAP.put(1, "Excellent");
        MAP.put(2, "Good");
        MAP.put(3, "Fair");
        MAP.put(4, "Poor");
        MAP.put(5, "Very poor");
        MAP.put(6, "Disconnected");
    }

    public static String of(int code) {
        String s = MAP.get(code);
        return (s != null) ? s : "Unknown";
    }
}
