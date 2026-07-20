package com.openim.tophone.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Downloads an APK into app-owned storage and opens the Android installer. */
public final class ApkUpdateInstaller {
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess();

        void onFailure(String message);
    }

    private ApkUpdateInstaller() {
    }

    public static void downloadAndInstall(Context context, String downloadUrl, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            File apkFile = null;
            try {
                if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
                    throw new IllegalArgumentException("升级地址为空");
                }
                File downloadDir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (downloadDir == null) {
                    throw new IllegalStateException("无法创建升级下载目录");
                }
                if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                    throw new IllegalStateException("无法创建升级下载目录");
                }
                apkFile = new File(downloadDir, "tophone_device_update.apk");

                Request request = new Request.Builder()
                        .url(downloadUrl.trim())
                        .get()
                        .build();
                try (Response response = CLIENT.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IllegalStateException("下载失败 HTTP " + response.code());
                    }
                    try (InputStream input = response.body().byteStream();
                         OutputStream output = new FileOutputStream(apkFile)) {
                        byte[] buffer = new byte[16 * 1024];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                        }
                        output.flush();
                    }
                }

                File completedApk = apkFile;
                MAIN.post(() -> {
                    try {
                        openInstaller(app, completedApk);
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    } catch (Exception e) {
                        if (callback != null) {
                            callback.onFailure(e.getMessage() != null ? e.getMessage() : "无法打开安装界面");
                        }
                    }
                });
            } catch (Exception e) {
                if (apkFile != null) {
                    // 删除本次未完成的临时包，避免下次误用残缺文件。
                    //noinspection ResultOfMethodCallIgnored
                    apkFile.delete();
                }
                String message = e.getMessage() != null ? e.getMessage() : "升级包下载失败";
                MAIN.post(() -> {
                    if (callback != null) {
                        callback.onFailure(message);
                    }
                });
            }
        });
    }

    private static void openInstaller(Context context, File apkFile) {
        Uri apkUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                apkFile
        );
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, APK_MIME_TYPE);
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(installIntent);
    }
}
