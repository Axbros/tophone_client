package com.openim.tophone.openim.entity;

public class DeviceLoginResp {
    public int code;
    public String msg;
    public DeviceLoginData data;

    public static class DeviceLoginData {
        public String deviceCode;
        public String username;
        /** 兼容字段，等于 deviceCode */
        public String userID;
        public String accountType;
        /** 所属项目组名称（tp_device_group.name），用于 check_version */
        public String groupName;
        public long groupId;
    }
}
