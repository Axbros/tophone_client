package com.openim.tophone.openim.entity;

public class CurrentVersionReq {
    public CurrentVersionReq(String version,String groupName) {
        this.version = version;
        this.groupName = groupName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    private String version;

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    private String groupName;
}
