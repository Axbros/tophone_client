package com.openim.tophone.openim.entity;

public class MqttTokenResp {
    public int code;
    public String msg;
    public MqttTokenData data;

    public static class MqttTokenData {
        public String mqttToken;
        public String username;
        public String brokerTCP;
        public Integer expiresIn;
    }
}
