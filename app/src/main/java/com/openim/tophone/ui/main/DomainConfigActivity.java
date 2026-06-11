package com.openim.tophone.ui.main;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.openim.tophone.MainApplication;
import com.openim.tophone.R;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DomainManager;
import com.openim.tophone.utils.ServerEndpointHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DomainConfigActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView tvCurrentHost;
    private TextView tvLocalModeNotice;
    private TextView tvHostLabel;
    private EditText etServerHost;
    private TextView tvTestResult;
    private Button btnTestPing;
    private Button btnSaveRestart;
    private Button btnResetDefault;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_domain_config);

        tvCurrentHost = findViewById(R.id.tv_current_host);
        tvLocalModeNotice = findViewById(R.id.tv_local_mode_notice);
        tvHostLabel = findViewById(R.id.tv_host_label);
        etServerHost = findViewById(R.id.et_server_host);
        tvTestResult = findViewById(R.id.tv_test_result);
        btnTestPing = findViewById(R.id.btn_test_ping);
        btnSaveRestart = findViewById(R.id.btn_save_restart);
        btnResetDefault = findViewById(R.id.btn_reset_default);

        refreshUiState();

        btnTestPing.setOnClickListener(v -> testPing());
        btnSaveRestart.setOnClickListener(v -> saveAndRestart());
        btnResetDefault.setOnClickListener(v -> resetToDefault());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void refreshUiState() {
        if (Constants.USE_LOCAL_LAN) {
            tvCurrentHost.setText(Constants.getLocalManagementBase());
            tvLocalModeNotice.setVisibility(View.VISIBLE);
            tvHostLabel.setVisibility(View.GONE);
            etServerHost.setVisibility(View.GONE);
            btnTestPing.setVisibility(View.GONE);
            btnSaveRestart.setVisibility(View.GONE);
            btnResetDefault.setVisibility(View.GONE);
            return;
        }

        String current = Constants.getCurrentHost();
        String builtIn = Constants.getBuiltInRemoteHost();
        String cached = DomainManager.getHost(this);
        String source = (cached != null && !cached.isEmpty()) ? getString(R.string.domain_source_custom) : getString(R.string.domain_source_default);
        tvCurrentHost.setText(current + "\n" + getString(R.string.domain_builtin_host, builtIn) + " · " + source);
        etServerHost.setText(current);
        etServerHost.setSelection(etServerHost.getText().length());
    }

    private void testPing() {
        String host = ServerEndpointHelper.normalizeHost(etServerHost.getText().toString());
        if (!ServerEndpointHelper.isValidHost(host)) {
            Toast.makeText(this, R.string.domain_host_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true);
        tvTestResult.setVisibility(View.VISIBLE);
        tvTestResult.setText(R.string.server_ping_measuring);
        tvTestResult.setTextColor(getColor(android.R.color.black));

        executor.execute(() -> {
            try {
                long ms = ServerEndpointHelper.probePingMs(host);
                runOnUiThread(() -> {
                    setBusy(false);
                    tvTestResult.setText(getString(R.string.domain_test_ok, ms));
                    tvTestResult.setTextColor(getColor(android.R.color.holo_green_dark));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    tvTestResult.setText(getString(R.string.domain_test_failed, e.getMessage()));
                    tvTestResult.setTextColor(getColor(android.R.color.holo_red_dark));
                });
            }
        });
    }

    private void saveAndRestart() {
        String host = ServerEndpointHelper.normalizeHost(etServerHost.getText().toString());
        if (!ServerEndpointHelper.isValidHost(host)) {
            Toast.makeText(this, R.string.domain_host_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true);
        tvTestResult.setVisibility(View.VISIBLE);
        tvTestResult.setText(R.string.server_ping_measuring);

        executor.execute(() -> {
            try {
                ServerEndpointHelper.probePingMs(host);
                runOnUiThread(() -> applyHostAndRestart(host));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    tvTestResult.setText(getString(R.string.domain_test_failed, e.getMessage()));
                    tvTestResult.setTextColor(getColor(android.R.color.holo_red_dark));
                    Toast.makeText(this, R.string.domain_save_need_ping, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void applyHostAndRestart(String host) {
        DomainManager.saveHost(this, host);
        Constants.updateHost(host);
        ((MainApplication) getApplication()).initNet();
        Toast.makeText(this, R.string.domain_save_success, Toast.LENGTH_SHORT).show();
        restartApp(this);
    }

    private void resetToDefault() {
        DomainManager.clear(this);
        Constants.resetToBuiltInHost();
        ((MainApplication) getApplication()).initNet();
        Toast.makeText(this, R.string.domain_reset_success, Toast.LENGTH_SHORT).show();
        restartApp(this);
    }

    private void setBusy(boolean busy) {
        btnTestPing.setEnabled(!busy);
        btnSaveRestart.setEnabled(!busy);
        btnResetDefault.setEnabled(!busy);
        etServerHost.setEnabled(!busy);
    }

    public static void restartApp(Context context) {
        Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) {
            return;
        }
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );
        context.startActivity(intent);
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
