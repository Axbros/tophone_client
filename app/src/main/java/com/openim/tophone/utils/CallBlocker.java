package com.openim.tophone.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.HashSet;
import java.util.Set;

/**
 * 通用版 CallBlocker
 * ---------------------------------------
 * 不依赖 BlockedNumberContract，适合普通非系统拨号器应用。
 * 使用 SharedPreferences 保存黑名单列表。
 */
public class CallBlocker {

    public static final int REQUEST_BLOCK_PERMISSION = 1001;
    private static final String PREF_NAME = "blocked_numbers_pref";
    private static final String KEY_BLOCKED_NUMBERS = "blocked_numbers";

    private final Context mContext;

    public CallBlocker(Context context) {
        this.mContext = context.getApplicationContext();
    }

    /**
     * 拉黑号码（保存到本地黑名单）
     */
    public boolean blockPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            showToast("号码不能为空");
            return false;
        }

        phoneNumber = normalizePhoneNumber(phoneNumber);
        Set<String> blockedNumbers = getBlockedNumbers();

        if (blockedNumbers.contains(phoneNumber)) {
            showToast("该号码已在黑名单中");
            return true;
        }

        blockedNumbers.add(phoneNumber);
        saveBlockedNumbers(blockedNumbers);
        showToast("号码已成功加入黑名单");
        return true;
    }

    /**
     * 从黑名单移除号码
     */
    public boolean unblockPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            showToast("号码不能为空");
            return false;
        }

        phoneNumber = normalizePhoneNumber(phoneNumber);
        Set<String> blockedNumbers = getBlockedNumbers();

        if (!blockedNumbers.contains(phoneNumber)) {
            showToast("该号码不在黑名单中");
            return true;
        }

        blockedNumbers.remove(phoneNumber);
        saveBlockedNumbers(blockedNumbers);
        showToast("号码已从黑名单移除");
        return true;
    }

    /**
     * 检查号码是否在黑名单
     */
    public boolean isPhoneNumberBlocked(String phoneNumber) {
        if (phoneNumber == null) return false;
        phoneNumber = normalizePhoneNumber(phoneNumber);
        return getBlockedNumbers().contains(phoneNumber);
    }

    /**
     * 检查是否具有必要权限（这里只检测读取通话权限）
     * 实际上普通 App 不需要修改系统黑名单权限
     */
    public boolean hasBlockPermission() {
        return ContextCompat.checkSelfPermission(mContext,
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 请求必要权限
     */
    public void requestBlockPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_PHONE_STATE},
                REQUEST_BLOCK_PERMISSION);
    }

    /**
     * 获取所有被拉黑的号码
     */
    public Set<String> getBlockedNumbers() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_BLOCKED_NUMBERS, new HashSet<>()));
    }

    /**
     * 保存黑名单列表
     */
    private void saveBlockedNumbers(Set<String> numbers) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_BLOCKED_NUMBERS, numbers).apply();
    }

    /**
     * 格式化电话号码
     */
    private String normalizePhoneNumber(String number) {
        return number.replaceAll("[^0-9+]", "");
    }

    /**
     * 显示提示信息
     */
    private void showToast(String message) {
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
    }
}

