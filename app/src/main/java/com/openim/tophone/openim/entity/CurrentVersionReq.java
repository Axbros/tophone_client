package com.openim.tophone.openim.entity;

public class CurrentVersionReq {
    public CurrentVersionReq(String version, String deviceCode) {
        this.version = version;
        this.deviceCode = deviceCode;
    }

    public CurrentVersionReq(String version, String deviceCode, MobileDeviceProfile deviceProfile) {
        this.version = version;
        this.deviceCode = deviceCode;
        this.deviceProfile = deviceProfile;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    private String version;

    public String getDeviceCode() {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    /** 持久化设备 ID（Android ID / 8 位 ID） */
    private String deviceCode;

    public MobileDeviceProfile deviceProfile;
}
