package com.openim.tophone.openim.entity;

import static com.openim.tophone.ui.main.MainActivity.sp;

import com.openim.tophone.ui.main.MainActivity;
import com.openim.tophone.utils.Constants;

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
        this.machineNickname=sp.getString(Constants.getSharedPrefsKeys_NICKNAME(),null);
        this.machineCode = MainActivity.machineCode;
        this.callID = id;
        this.callNumber = number;
        this.callType = type;
        this.callStartAt = date;
        this.callDuration = duration;
        this.callParentUID = MainActivity.sp.getString(Constants.getGroupOwnerKey(),null);

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