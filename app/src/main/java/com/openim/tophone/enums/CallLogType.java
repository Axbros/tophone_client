package com.openim.tophone.enums;

public enum CallLogType {
    CONNECTED("connected"),
    CALL_OUT("call_out"),
    CALL_IN("call_in");

    private final String description;

    CallLogType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
