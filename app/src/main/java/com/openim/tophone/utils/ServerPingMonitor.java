package com.openim.tophone.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
 * Periodically probes GET /ping and updates the bound latency label.
 * Runs while the app is in the foreground.
 */
public final class ServerPingMonitor {
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
            return;
        }
        if (lastDisplayText != null) {
            textView.setText(lastDisplayText);
        } else if (running.get()) {
            textView.setText(R.string.server_ping_measuring);
        }
    }

    public void start() {
        if (running.getAndSet(true)) {
            return;
        }
        showMeasuring();
        mainHandler.post(tick);
    }

    public void stop() {
        running.set(false);
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
            return;
        }
        executor.execute(() -> {
            PingResult measured = measurePing();
            if (measured.latencyMs <= 0 && measured.ok) {
                measured = PingResult.ok(1);
            }
            inFlight.set(false);
            if (!running.get()) {
                return;
            }
            final PingResult result = measured;
            mainHandler.post(() -> applyResult(result));
        });
    }

    private PingResult measurePing() {
        String url = Constants.getPingUrl();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return PingResult.error();
            }
            long rtt = response.receivedResponseAtMillis() - response.sentRequestAtMillis();
            if (rtt <= 0) {
                rtt = 1;
            }
            return PingResult.ok(rtt);
        } catch (IOException e) {
            return PingResult.error();
        }
    }

    private void applyResult(PingResult result) {
        if (!running.get()) {
            return;
        }
        Context context = appContext();
        if (context == null) {
            return;
        }
        if (!result.ok) {
            setDisplayText(context.getString(R.string.server_ping_offline));
            return;
        }
        setDisplayText(context.getString(R.string.server_ping_ms, result.latencyMs));
    }

    private void setDisplayText(CharSequence text) {
        lastDisplayText = text;
        TextView current = target;
        if (current != null) {
            current.setText(text);
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

        private PingResult(boolean ok, long latencyMs) {
            this.ok = ok;
            this.latencyMs = latencyMs;
        }

        static PingResult ok(long latencyMs) {
            return new PingResult(true, latencyMs);
        }

        static PingResult error() {
            return new PingResult(false, -1);
        }
    }
}
