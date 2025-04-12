package com.openim.tophone.utils;

import org.json.JSONObject;

public class ToPhone {
    private String TAG = "ToPhone Utils";
    private PhoneUtils phoneUtils = new PhoneUtils();

    public JSONObject handleMessage(String jsonStr) {
        try {
            // 将JSON字符串解析为JSONObject对象
            JSONObject jsonObject = new JSONObject(jsonStr);
            L.d(TAG, "message in json: " + jsonObject);
            // 获取各个键对应的值示例
            String type = jsonObject.getString("type");
            String mobile = jsonObject.getString("mobile");
            String content = jsonObject.getString("content");
            switch (type) {
                case "idle":
                    phoneUtils.hangUpCall();
                    break;
                case "answer":
                    phoneUtils.answerCall();
                    break;
                case "call":
                    phoneUtils.makePhoneCall(mobile);
                    break;
                case "send_message":
                    phoneUtils.sendSms(mobile, content);
                    break;
            }
            return jsonObject;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
