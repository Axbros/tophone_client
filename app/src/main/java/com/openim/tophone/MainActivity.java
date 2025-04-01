package com.openim.tophone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.openim.tophone.utils.DeviceUtils;

public class MainActivity extends AppCompatActivity {
    private static String accountID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        // 调用 run 方法获取 accountID
        run(getApplicationContext());

        // 获取 account_id_text 的引用
        TextView accountIdTextView = findViewById(R.id.account_id_text);
        // 设置 account_id_text 的文本为 accountID
        if (accountIdTextView != null) {
            accountIdTextView.setText(accountID);
        }
    }

    public static void run(Context context) {
        // 1.获取设备 ID
        accountID = DeviceUtils.getAndroidId(context) + "@tsinghua.edu.cn";
    }


    // 实现 openLink 方法
    public void openLink(View view) {
        // 这里假设要打开的链接是 www.tophone.cc
        String url = "https://www.tophone.cc";
        // 创建一个 Intent，用于打开链接
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        // 检查是否有应用程序可以处理该 Intent
        if (intent.resolveActivity(getPackageManager()) != null) {
            // 启动该 Intent
            startActivity(intent);
        }
    }
}