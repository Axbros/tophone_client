package com.openim.tophone.rtc;

import android.text.TextUtils;

/** VolcEngine RTC access token helpers (format: {@code 001} + 24-char appId + base64). */
public final class RtcTokenUtil {

    private static final String TOKEN_VERSION = "001";
    private static final int APP_ID_LENGTH = 24;
    private static final int TOKEN_PREFIX_LENGTH = 3 + APP_ID_LENGTH;

    private RtcTokenUtil() {
    }

    public static String extractAppId(String token) {
        if (TextUtils.isEmpty(token) || token.length() < TOKEN_PREFIX_LENGTH) {
            return null;
        }
        if (!token.startsWith(TOKEN_VERSION)) {
            return null;
        }
        return token.substring(3, TOKEN_PREFIX_LENGTH);
    }

    public static boolean isValidFormat(String token) {
        String appId = extractAppId(token);
        return appId != null && !appId.trim().isEmpty() && token.length() > TOKEN_PREFIX_LENGTH;
    }
}
