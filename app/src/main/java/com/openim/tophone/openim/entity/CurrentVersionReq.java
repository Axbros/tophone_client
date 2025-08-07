package com.openim.tophone.openim.entity;

public class CurrentVersionReq {
    public CurrentVersionReq(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    private String version;
}
