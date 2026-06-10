package com.openim.tophone.openim.entity;

public class CheckVersionDataResp {
        public String info;

        public Boolean isExist;

        public Integer timeOut;

        public String roomID;

        /** check_version 在 mqtt.enable=true 时返回 */
        public String mqttToken;
        public String mqttUsername;
        public String mqttBrokerTCP;
        public Integer mqttExpiresIn;
}
