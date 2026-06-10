package com.openim.tophone.ui.main.vm;

import com.openim.tophone.MainApplication;
import com.openim.tophone.base.BaseApp;
import com.openim.tophone.mqtt.MqttManager;
import com.openim.tophone.ui.main.MainActivity;
import com.openim.tophone.utils.Constants;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.lifecycle.MutableLiveData;

import com.openim.tophone.base.BaseViewModel;

public class UserVM extends BaseViewModel {
    public MutableLiveData<String> accountID = new MutableLiveData<>("");
    public MutableLiveData<String> groupInfoLabel = new MutableLiveData<>("Unknown");
    public MutableLiveData<Boolean> phonePermissions = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> smsPermissions = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> connectionStatus = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> checkedIn = new MutableLiveData<>();
    public MutableLiveData<Boolean> isGroupInfoVisible = new MutableLiveData<>(true);

    public void syncCheckInStatus(Context context) {
        SharedPreferences sp = context.getApplicationContext()
                .getSharedPreferences(Constants.getSharedPrefsKeys_FILE_NAME(), Context.MODE_PRIVATE);
        if (sp.contains(Constants.getCheckedInKey())) {
            checkedIn.setValue(sp.getBoolean(Constants.getCheckedInKey(), false));
        }
        refreshGroupInfoLabel(sp);
        connectionStatus.setValue(MqttManager.getInstance().isConnected());
    }

    private void refreshGroupInfoLabel(SharedPreferences sp) {
        String groupName = sp.getString(Constants.getGroupName(), null);
        String owner = sp.getString(Constants.getGroupOwnerKey(), null);
        if (groupName != null && !groupName.isEmpty()) {
            groupInfoLabel.setValue(groupName);
            isGroupInfoVisible.setValue(true);
        } else if (owner != null && !owner.isEmpty()) {
            groupInfoLabel.setValue("Owner: " + owner);
            isGroupInfoVisible.setValue(true);
        }
    }

    public void handleBtnConnect() {
        isLoading.setValue(true);
        boolean connected = Boolean.TRUE.equals(connectionStatus.getValue());
        if (connected) {
            MqttManager.getInstance().disconnect();
            connectionStatus.setValue(false);
            groupInfoLabel.setValue("已断开");
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
            Toast.makeText(context, "请先完成打卡后再连接", Toast.LENGTH_SHORT).show();
            connectionStatus.setValue(false);
            return;
        }
        String groupName = sp.getString(Constants.getGroupName(), MainActivity.machineCode);
        Toast.makeText(context, "正在重新连接…", Toast.LENGTH_SHORT).show();
        MainApplication mainApp = (MainApplication) context.getApplicationContext();
        mainApp.triggerMqttReconnect(groupName);
    }
}
