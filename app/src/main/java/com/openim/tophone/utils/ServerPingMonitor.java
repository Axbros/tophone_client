package com.openim.tophone.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.openim.tophone.R;
import com.openim.tophone.base.BaseApp;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Periodically probes GET /api/v1/ping and updates the bound latency label.
 * Runs while the app is in the foreground.
 */
public final class ServerPingMonitor {
    private static final String TAG = "ServerPing";
    private static final ServerPingMonitor INSTANCE = new ServerPingMonitor();
    private static final long INTERVAL_MS = 3_000L;
    private static final int TIMEOUT_SEC = 5;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Nullable
    private TextView target;
    @Nullable
    private CharSequence lastDisplayText;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running.get()) {
                return;
            }
            probeOnce();
            mainHandler.postDelayed(this, INTERVAL_MS);
        }
    };

    public static ServerPingMonitor getInstance() {
        return INSTANCE;
    }

    private ServerPingMonitor() {
    }

    public void bind(@Nullable TextView textView) {
        target = textView;
        if (textView == null) {
            Log.w(TAG, "bind: ping TextView is null, UI will not update");
            return;
        }
        Log.i(TAG, "bind: attached to " + textView.getClass().getSimpleName()
                + " id=" + textView.getId());
        if (lastDisplayText != null) {
            textView.setText(lastDisplayText);
            Log.d(TAG, "bind: restored lastDisplayText=" + lastDisplayText);
        } else if (running.get()) {
            textView.setText(R.string.server_ping_measuring);
        }
    }

    public void start() {
        if (running.getAndSet(true)) {
            Log.d(TAG, "start: already running, url=" + Constants.getPingUrl());
            return;
        }
        Log.i(TAG, "start: primary=" + Constants.getPingUrlPrimary()
                + " fallback=" + Constants.getPingUrlFallback()
                + " host=" + Constants.getCurrentHost()
                + " localLan=" + Constants.USE_LOCAL_LAN);
        showMeasuring();
        mainHandler.post(tick);
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        Log.i(TAG, "stop");
        mainHandler.removeCallbacks(tick);
        inFlight.set(false);
    }

    private void showMeasuring() {
        Context context = appContext();
        if (context != null) {
            setDisplayText(context.getString(R.string.server_ping_measuring));
        }
    }

    private void probeOnce() {
        if (!inFlight.compareAndSet(false, true)) {
            Log.d(TAG, "probeOnce: skipped, previous request still in flight");
            return;
        }
        String url = Constants.getPingUrlPrimary();
        Log.d(TAG, "probeOnce: GET " + url + " (fallback " + Constants.getPingUrlFallback() + ")");
        executor.execute(() -> {
            PingResult measured = measurePingWithFallback();
            if (measured.latencyMs <= 0 && measured.ok) {
                measured = PingResult.ok(1);
            }
            inFlight.set(false);
            if (!running.get()) {
                Log.d(TAG, "probeOnce: monitor stopped, ignore result");
                return;
            }
            final PingResult result = measured;
            mainHandler.post(() -> applyResult(result));
        });
    }

    private PingResult measurePingWithFallback() {
        String primary = Constants.getPingUrlPrimary();
        PingResult primaryResult = measurePing(primary);
        if (primaryResult.ok) {
            return primaryResult;
        }
        if (primaryResult.httpCode != 404) {
            return primaryResult;
        }
        String fallback = Constants.getPingUrlFallback();
        Log.i(TAG, "measurePingWithFallback: primary 404, try " + fallback);
        return measurePing(fallback);
    }

    private PingResult measurePing(String url) {
        Request request = new Request.Builder().url(url).get().build();
        long startMs = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            long elapsed = System.currentTimeMillis() - startMs;
            long rtt = response.receivedResponseAtMillis() - response.sentRequestAtMillis();
            if (rtt <= 0) {
                rtt = elapsed;
            }
            if (!response.isSuccessful()) {
                Log.w(TAG, "measurePing: HTTP " + code + " url=" + url
                        + " elapsed=" + elapsed + "ms");
                return PingResult.error(code, "HTTP " + code);
            }
            Log.i(TAG, "measurePing: ok url=" + url + " code=" + code
                    + " rtt=" + rtt + "ms elapsed=" + elapsed + "ms");
            return PingResult.ok(rtt);
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            Log.w(TAG, "measurePing: failed url=" + url
                    + " elapsed=" + elapsed + "ms error=" + e.getMessage(), e);
            return PingResult.error(-1, e.getMessage());
        }
    }

    private void applyResult(PingResult result) {
        if (!running.get()) {
            return;
        }
        Context context = appContext();
        if (context == null) {
            Log.w(TAG, "applyResult: app context null");
            return;
        }
        CharSequence display;
        if (!result.ok) {
            display = context.getString(R.string.server_ping_offline);
            Log.w(TAG, "applyResult: offline reason=" + result.error
                    + " httpCode=" + result.httpCode
                    + " target=" + (target != null ? "set" : "null"));
        } else {
            display = context.getString(R.string.server_ping_ms, result.latencyMs);
            Log.i(TAG, "applyResult: display=" + display
                    + " target=" + (target != null ? "set" : "null"));
        }
        setDisplayText(display);
    }

    private void setDisplayText(CharSequence text) {
        lastDisplayText = text;
        TextView current = target;
        if (current != null) {
            current.setText(text);
        } else {
            Log.w(TAG, "setDisplayText: no TextView bound, text=" + text);
        }
    }

    @Nullable
    private Context appContext() {
        BaseApp app = BaseApp.inst();
        return app != null ? app.getApplicationContext() : null;
    }

    private static final class PingResult {
        final boolean ok;
        final long latencyMs;
        final int httpCode;
        @Nullable
        final String error;

        private PingResult(boolean ok, long latencyMs, int httpCode, @Nullable String error) {
            this.ok = ok;
            this.latencyMs = latencyMs;
            this.httpCode = httpCode;
            this.error = error;
        }

        static PingResult ok(long latencyMs) {
            return new PingResult(true, latencyMs, 200, null);
        }

        static PingResult error(int httpCode, @Nullable String error) {
            return new PingResult(false, -1, httpCode, error);
        }
    }
}
