package com.openim.tophone.openim.entity;

public class PhoneLocationResponse {
    public int code;
    public String shengfen;  // 省份
    public String chengshi;  // 城市
    public String fuwushang; // 服务商

    // 如果 code 不是 200 的话，给一个默认值
    public String getLocation() {
        if (code == 200) {
            return shengfen + "·" + chengshi + "·" + fuwushang;
        } else {
            return "中國·大陸";
        }
    }
}
