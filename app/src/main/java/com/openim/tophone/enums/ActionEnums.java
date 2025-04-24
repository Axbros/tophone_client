package com.openim.tophone.enums;

public enum ActionEnums {

    SEND_SMS("send_sms"),
    RECEIVED_SMS("received_sms"),

    INCOME("income"),

    IDLE("idle"),

    ANSWER("answer"),

    CALL("call")

    ;

    //receive

    private String type;

    ActionEnums(String type){
        this.type=type;
    }

    public String getType(){
        return type;
    }
}
