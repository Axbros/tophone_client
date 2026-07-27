package com.openim.tophone.openim.entity;

import android.content.Context;
import android.content.SharedPreferences;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.DeviceUtils;

public class CallLogBean {


    private String machineNickname;
    private String machineCode;

    private long callID;
    private String callNumber;
    private int callType;
    private String callStartAt;
    private int callDuration;

    private String callParentUID;

    // 构造方法、Getter 和 Setter
    public CallLogBean(long id, String number, int type, String date, int duration) {
        SharedPreferences sharedPreferences = BaseApp.inst().getSharedPreferences(
                Constants.getSharedPrefsKeys_FILE_NAME(),
                Context.MODE_PRIVATE
        );
        this.machineNickname = sharedPreferences.getString(
                Constants.getSharedPrefsKeys_NICKNAME(),
                ""
        );
        this.machineCode = DeviceUtils.getOrCreateClientDeviceId(BaseApp.inst());
        this.callID = id;
        this.callNumber = number;
        this.callType = type;
        this.callStartAt = date;
        this.callDuration = duration;
        this.callParentUID = sharedPreferences.getString(Constants.getGroupOwnerKey(), "");
    }

    public String getMachineNickname() {
        return machineNickname;
    }

    public void setMachineNickname(String machineNickname) {
        this.machineNickname = machineNickname;
    }

    public String getMachineCode() {
        return machineCode;
    }

    public void setMachineCode(String machineCode) {
        this.machineCode = machineCode;
    }

    public long getCallID() {
        return callID;
    }

    public void setCallID(long callID) {
        this.callID = callID;
    }

    public String getCallNumber() {
        return callNumber;
    }

    public void setCallNumber(String callNumber) {
        this.callNumber = callNumber;
    }

    public int getCallType() {
        return callType;
    }

    public void setCallType(int callType) {
        this.callType = callType;
    }

    public String getCallStartAt() {
        return callStartAt;
    }

    public void setCallStartAt(String callStartAt) {
        this.callStartAt = callStartAt;
    }

    public int getCallDuration() {
        return callDuration;
    }

    public void setCallDuration(int callDuration) {
        this.callDuration = callDuration;
    }

    public String getParentUID() {
        return callParentUID;
    }

    public void setParentUID(String parentUID) {
        this.callParentUID = parentUID;
    }
}
