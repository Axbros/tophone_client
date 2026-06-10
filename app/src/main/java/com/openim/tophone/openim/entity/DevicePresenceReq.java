package com.openim.tophone.openim.entity;

public class DevicePresenceReq {
    public String deviceId;
    public boolean online;

    public DevicePresenceReq(String deviceId, boolean online) {
        this.deviceId = deviceId;
        this.online = online;
    }
}
