package com.openim.tophone.openim.entity;

public class PhoneLocationResponse {
    public int code;
    public String msg;
    public PhoneLocationData data;

    public static class PhoneLocationData {
        public String province;
        public String city;
        public String serviceProvider;
        public String display;
    }

    public String getDisplay() {
        if (code != 0 || data == null) {
            return "";
        }
        if (data.display != null && !data.display.trim().isEmpty()) {
            return data.display.trim();
        }
        StringBuilder out = new StringBuilder();
        appendPart(out, data.province);
        appendPart(out, data.city);
        appendPart(out, data.serviceProvider);
        return out.toString();
    }

    private static void appendPart(StringBuilder out, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            out.append("·");
        }
        out.append(value.trim());
    }
}
