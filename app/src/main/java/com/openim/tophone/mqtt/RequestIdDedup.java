package com.openim.tophone.mqtt;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * QoS1 至少一次送达时的 requestId 去重，避免重复拨号/发短信。
 * 保留最近 100 条或 10 分钟内的 requestId。
 */
public class RequestIdDedup {

    private static final int MAX_SIZE = 100;
    private static final long TTL_MS = 10 * 60 * 1000L;
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>(MAX_SIZE, 0.75f, true);

    public synchronized boolean isDuplicate(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return false;
        }
        purgeExpired();
        if (seen.containsKey(requestId)) {
            return true;
        }
        seen.put(requestId, System.currentTimeMillis());
        while (seen.size() > MAX_SIZE) {
            Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        return false;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > TTL_MS) {
                it.remove();
            }
        }
    }
}
