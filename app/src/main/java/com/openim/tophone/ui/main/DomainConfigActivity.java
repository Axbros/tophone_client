package com.openim.tophone.ui.main;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.openim.tophone.R;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DomainManager;

public class DomainConfigActivity extends AppCompatActivity {

    // ===== 线路一：主域名 + 路径 =====
    private static final String HOST_LINE_1 = "https://api-v3.huahaiye.cn";
    private static final String PATH_LINE_1 = "/api-management/api/v1/domain/tophone";

    // ===== 线路二：主域名 + 路径（你说主域名是 api.tophone.cc）=====
    private static final String HOST_LINE_2 = "https://api.tophone.cc";
    private static final String PATH_LINE_2 = "/api/v1/domain/tophone"; // 如果你们线路二真实路径不同，就改这里

    private TextView tvCurrentHost;
    private Spinner spinner;
    private Button btnTestSave;

    private final List<LineItem> lines = new ArrayList<>();
    private LineItem selected;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_domain_config);

        tvCurrentHost = findViewById(R.id.tv_current_host);
        spinner = findViewById(R.id.spinner_line);
        btnTestSave = findViewById(R.id.btn_test_save);

        initLines();
        initSpinner();
        refreshCurrentHostText();

        btnTestSave.setOnClickListener(v -> testAndSave());
    }

    private void initLines() {
        lines.clear();
        lines.add(new LineItem("线路一", HOST_LINE_1, PATH_LINE_1));
        lines.add(new LineItem("线路二", HOST_LINE_2, PATH_LINE_2));
    }

    private void initSpinner() {
        List<String> display = new ArrayList<>();
        for (LineItem item : lines) display.add(item.label); // 只显示线路名

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                display
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // 默认选中：线路一（你也可以根据缓存 host 来决定选中哪个线路）
        spinner.setSelection(0);
        selected = lines.get(0);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                selected = lines.get(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void testAndSave() {
        if (selected == null) return;

        btnTestSave.setEnabled(false);

        String testUrl = joinUrl(selected.host, selected.path);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(testUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int httpCode = conn.getResponseCode();
                InputStream is = (httpCode >= 200 && httpCode < 400)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                String body = sb.toString();

                runOnUiThread(() -> {
                    btnTestSave.setEnabled(true);

                    if (httpCode != 200) {
                        return;
                    }

                    try {
                        JSONObject obj = new JSONObject(body);
                        int bizCode = obj.optInt("code", -1);
                        JSONObject data = obj.optJSONObject("data");
                        String value = data != null ? data.optString("value", "") : "";

                        if (bizCode == 0 && value != null && !value.isEmpty()) {
                            // 🎯 保存的是返回的 data.value（不是线路 host）
                            DomainManager.saveHost(DomainConfigActivity.this, value);
                            Constants.updateHost(value);

                            refreshCurrentHostText();
                            Toast.makeText(this,"Update the domain successfully!",Toast.LENGTH_LONG).show();

                            // 保存后退出
                            finish();
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                restartApp(this);
                            }, 1000);
                        } else {
                            tvCurrentHost.setText("返回异常：\n" + body);
                        }

                    } catch (Exception e) {
                        tvCurrentHost.setText("解析失败：" + e.getMessage());
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnTestSave.setEnabled(true);
                    tvCurrentHost.setText("请求失败：" + e.getMessage());
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void refreshCurrentHostText() {
        String host = Constants.DEFAULT_HOST;

        // 如果当前 host 已被更新，优先用它
        try {
            // CURRENT_HOST 是 private，这里通过 URL 反推不合适
            // 所以直接用缓存 or DEFAULT
            String cached = DomainManager.getHost(this);
            if (cached != null && !cached.isEmpty()) {
                host = cached;
            }
        } catch (Exception ignored) {}

        tvCurrentHost.setText(host);
    }

    private static String joinUrl(String host, String path) {
        if (host == null) host = "";
        if (path == null) path = "";
        if (host.endsWith("/") && path.startsWith("/")) return host + path.substring(1);
        if (!host.endsWith("/") && !path.startsWith("/")) return host + "/" + path;
        return host + path;
    }

    private static class LineItem {
        final String label;
        final String host; // 线路探测 host（可能不同）
        final String path; // 线路探测 path（可能不同）

        LineItem(String label, String host, String path) {
            this.label = label;
            this.host = host;
            this.path = path;
        }
    }

    public static void restartApp(Context context) {
        Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) return;

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );
        context.startActivity(intent);

        // 可选：彻底杀掉当前进程，确保全量重启
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}