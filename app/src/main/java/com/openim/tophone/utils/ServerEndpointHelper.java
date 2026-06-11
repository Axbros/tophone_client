package com.openim.tophone.utils;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ServerEndpointHelper {
    private static final Pattern HOST_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$|^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$"
    );
    private static final int TIMEOUT_SEC = 8;

    private ServerEndpointHelper() {
    }

    @NonNull
    public static String normalizeHost(String input) {
        if (input == null) {
            return "";
        }
        String host = input.trim();
        if (host.startsWith("https://")) {
            host = host.substring(8);
        } else if (host.startsWith("http://")) {
            host = host.substring(7);
        }
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        return host.trim();
    }

    public static boolean isValidHost(String host) {
        String normalized = normalizeHost(host);
        if (normalized.isEmpty()) {
            return false;
        }
        return HOST_PATTERN.matcher(normalized).matches()
                || normalized.matches("^\\d{1,3}(\\.\\d{1,3}){3}$");
    }

    public static String pingUrlForHost(String host) {
        return "https://" + normalizeHost(host) + "/ping";
    }

    public static String mqttWssUrlForHost(String host) {
        return "wss://" + normalizeHost(host) + "/mqtt";
    }

    public static long probePingMs(String host) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(pingUrlForHost(host))
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            long rtt = response.receivedResponseAtMillis() - response.sentRequestAtMillis();
            return Math.max(1, rtt);
        }
    }
}
