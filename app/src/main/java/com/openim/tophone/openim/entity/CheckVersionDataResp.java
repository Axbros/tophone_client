package com.openim.tophone.openim.entity;

public class CheckVersionDataResp {
        public String info;

        public Boolean isExist;

        public Boolean waitAssign;
        public Boolean waitCheckIn;

        public String groupName;
        public Long groupId;
        public String deviceCode;

        public Integer timeOut;

        public String roomID;

        public Boolean voiceDisabled;
        public Boolean smsDisabled;
        public Integer status;

        /** check_version 在 mqtt.enable=true 时返回（扁平字段，兼容旧版） */
        public String mqttToken;
        public String mqttUsername;
        public String mqttBrokerTCP;
        public Integer mqttExpiresIn;

        /** 嵌套 mqtt 配置（新版） */
        public MqttConfig mqtt;

        public static class MqttConfig {
                public Boolean enable;
                public String broker;
                public String clientId;
                public String username;
                public String password;
        }

        public boolean hasMqttCredentials() {
                if (mqtt != null && Boolean.TRUE.equals(mqtt.enable)
                                && mqtt.password != null && !mqtt.password.isEmpty()) {
                        return true;
                }
                return mqttToken != null && !mqttToken.isEmpty();
        }

        public String resolveMqttToken() {
                if (mqtt != null && mqtt.password != null && !mqtt.password.trim().isEmpty()) {
                        return mqtt.password.trim();
                }
                return mqttToken != null ? mqttToken.trim() : null;
        }

        public String resolveMqttUsername(String deviceId) {
                if (mqtt != null && mqtt.username != null && !mqtt.username.trim().isEmpty()) {
                        return mqtt.username.trim();
                }
                if (mqttUsername != null && !mqttUsername.trim().isEmpty()) {
                        return mqttUsername.trim();
                }
                return "device_" + deviceId;
        }

        public String resolveMqttBroker() {
                if (mqtt != null && mqtt.broker != null && !mqtt.broker.isEmpty()) {
                        return mqtt.broker;
                }
                return mqttBrokerTCP;
        }
}
