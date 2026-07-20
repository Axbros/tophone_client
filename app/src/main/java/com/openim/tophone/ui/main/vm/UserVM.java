package com.openim.tophone.ui.main.vm;

import com.openim.tophone.MainApplication;
import com.openim.tophone.R;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.mqtt.MqttManager;
import com.openim.tophone.ui.main.MainActivity;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.L;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.lifecycle.MutableLiveData;

import com.openim.tophone.base.BaseViewModel;

public class UserVM extends BaseViewModel {
    private static final String TAG = "UserVM";

    public MutableLiveData<String> accountID = new MutableLiveData<>("");
    public MutableLiveData<String> groupInfoLabel = new MutableLiveData<>("Unknown");
    /** true = voice enabled, false = disabled */
    public MutableLiveData<Boolean> phoneStatus = new MutableLiveData<>(true);
    /** true = SMS enabled, false = disabled */
    public MutableLiveData<Boolean> smsStatus = new MutableLiveData<>(true);
    public MutableLiveData<Boolean> connectionStatus = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> checkedIn = new MutableLiveData<>();
    public MutableLiveData<Boolean> isGroupInfoVisible = new MutableLiveData<>(false);
    /** Show pairing QR while waiting for group assignment */
    public MutableLiveData<Boolean> showPairingQr = new MutableLiveData<>(false);
    /** Call log, status, and connection UI — only after device is assigned to a group */
    public MutableLiveData<Boolean> showBoundFeatures = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> showRoomSwitch = new MutableLiveData<>(false);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void syncCheckInStatus(Context context) {
        SharedPreferences sp = context.getApplicationContext()
                .getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE);
        if (sp.contains(Constants.getCheckedInKey())) {
            checkedIn.setValue(sp.getBoolean(Constants.getCheckedInKey(), false));
        }
        refreshGroupInfoLabel(sp);
        if (Boolean.TRUE.equals(showBoundFeatures.getValue())) {
            loadSavedPolicy(sp);
        }
        connectionStatus.setValue(MqttManager.getInstance().isConnected());
        refreshRoomSwitchVisibility(sp);
    }

    private void refreshRoomSwitchVisibility(SharedPreferences sp) {
        boolean checked = sp.getBoolean(Constants.getCheckedInKey(), false);
        String roomID = sp.getString(Constants.getAssignedRoomIDKey(), "");
        showRoomSwitch.setValue(checked && !TextUtils.isEmpty(roomID));
    }

    private void loadSavedPolicy(SharedPreferences sp) {
        if (!Boolean.TRUE.equals(showBoundFeatures.getValue())) {
            return;
        }
        if (!sp.contains(Constants.getVoiceDisabledKey())) {
            return;
        }
        applyDevicePolicy(
                sp.getBoolean(Constants.getVoiceDisabledKey(), false),
                sp.getBoolean(Constants.getSmsDisabledKey(), false),
                sp.getInt(Constants.getDevicePolicyStatusKey(), 1),
                false
        );
    }

    public void applyDevicePolicy(boolean voiceDisabled, boolean smsDisabled, int status) {
        applyDevicePolicy(voiceDisabled, smsDisabled, status, true);
    }

    public void applyDevicePolicy(boolean voiceDisabled, boolean smsDisabled, int status, boolean notify) {
        mainHandler.post(() -> applyDevicePolicyOnMain(voiceDisabled, smsDisabled, status, notify));
    }

    private void applyDevicePolicyOnMain(boolean voiceDisabled, boolean smsDisabled, int status, boolean notify) {
        if (!Boolean.TRUE.equals(showBoundFeatures.getValue())) {
            return;
        }
        boolean paused = status == 3;
        boolean phoneEnabled = !paused && !voiceDisabled;
        boolean smsEnabled = !paused && !smsDisabled;

        Boolean prevPhone = phoneStatus.getValue();
        Boolean prevSms = smsStatus.getValue();

        phoneStatus.setValue(phoneEnabled);
        smsStatus.setValue(smsEnabled);
        persistPolicy(voiceDisabled, smsDisabled, status);

        L.i(TAG, "policy updated voiceDisabled=" + voiceDisabled
                + " smsDisabled=" + smsDisabled + " status=" + status
                + " phoneEnabled=" + phoneEnabled + " smsEnabled=" + smsEnabled);

        if (!notify) {
            return;
        }
        Context ctx = BaseApp.inst();
        if (ctx == null) {
            return;
        }
        if (paused && (prevPhone == null || prevPhone || prevSms == null || prevSms)) {
            toast(ctx, R.string.toast_device_paused);
            return;
        }
        if (prevPhone != null && prevPhone != phoneEnabled) {
            toast(ctx, phoneEnabled ? R.string.toast_voice_enabled : R.string.toast_voice_disabled);
        }
        if (prevSms != null && prevSms != smsEnabled) {
            toast(ctx, smsEnabled ? R.string.toast_sms_enabled : R.string.toast_sms_disabled);
        }
    }

    private void persistPolicy(boolean voiceDisabled, boolean smsDisabled, int status) {
        Context ctx = BaseApp.inst();
        if (ctx == null) {
            return;
        }
        ctx.getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE)
                .edit()
                .putBoolean(Constants.getVoiceDisabledKey(), voiceDisabled)
                .putBoolean(Constants.getSmsDisabledKey(), smsDisabled)
                .putInt(Constants.getDevicePolicyStatusKey(), status)
                .apply();
    }

    private void toast(Context ctx, int resId) {
        Toast.makeText(ctx, resId, Toast.LENGTH_LONG).show();
    }

    private void refreshGroupInfoLabel(SharedPreferences sp) {
        String groupName = sp.getString(Constants.getGroupName(), null);
        String owner = sp.getString(Constants.getGroupOwnerKey(), null);
        if (groupName != null && !groupName.isEmpty()) {
            groupInfoLabel.setValue(groupName);
            isGroupInfoVisible.setValue(true);
            showPairingQr.setValue(false);
            showBoundFeatures.setValue(true);
            return;
        }
        showBoundFeatures.setValue(false);
        if (owner != null && !owner.isEmpty()) {
            groupInfoLabel.setValue("Owner: " + owner);
            isGroupInfoVisible.setValue(true);
        } else if (!Boolean.TRUE.equals(showPairingQr.getValue())) {
            isGroupInfoVisible.setValue(false);
        }
    }

    public void updateBindState(boolean waitAssign, String groupName) {
        mainHandler.post(() -> {
            if (groupName != null && !groupName.isEmpty()) {
                groupInfoLabel.setValue(groupName);
                isGroupInfoVisible.setValue(true);
                showPairingQr.setValue(false);
                showBoundFeatures.setValue(true);
                return;
            }
            showPairingQr.setValue(waitAssign);
            if (waitAssign) {
                isGroupInfoVisible.setValue(false);
                showBoundFeatures.setValue(false);
            }
        });
    }

    public void handleBtnConnect() {
        Context context = BaseApp.inst();
        isLoading.setValue(true);
        boolean connected = Boolean.TRUE.equals(connectionStatus.getValue());
        if (connected) {
            MqttManager.getInstance().disconnect();
            connectionStatus.setValue(false);
            if (context != null) {
                groupInfoLabel.setValue(context.getString(R.string.toast_disconnected));
            }
        } else {
            reconnectMqtt();
        }
        isLoading.setValue(false);
    }

    private void reconnectMqtt() {
        Context context = BaseApp.inst();
        SharedPreferences sp = context.getSharedPreferences(
                Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE);
        boolean checkedIn = sp.getBoolean(Constants.getCheckedInKey(), false);
        if (!checkedIn) {
            Toast.makeText(context, R.string.toast_check_in_required, Toast.LENGTH_SHORT).show();
            connectionStatus.setValue(false);
            return;
        }
        String groupName = sp.getString(Constants.getGroupName(), MainActivity.machineCode);
        Toast.makeText(context, R.string.toast_reconnecting, Toast.LENGTH_SHORT).show();
        MainApplication mainApp = (MainApplication) context.getApplicationContext();
        mainApp.triggerMqttReconnect(groupName);
    }
}
