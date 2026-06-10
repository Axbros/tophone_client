package com.openim.tophone.rtc;

import android.util.SparseArray;

public final class NetworkQualityText {
    private NetworkQualityText() {}

    private static final SparseArray<String> MAP = new SparseArray<>();

    static {
        MAP.put(0, "未知");
        MAP.put(1, "极好");
        MAP.put(2, "好");
        MAP.put(3, "较差但不影响沟通");
        MAP.put(4, "差沟通不顺畅");
        MAP.put(5, "非常差");
        MAP.put(6, "网络连接断开，无法通话。网络可能由于 12s 内无应答、开启飞行模式、拔掉网线等原因断开。");
    }

    public static String of(int code) {
        String s = MAP.get(code);
        return (s != null) ? s : "未知";
    }
}
