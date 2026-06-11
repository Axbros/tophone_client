package com.openim.tophone.mqtt;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 短信接收短时间去重，避免广播重复触发导致重复 publish。
 */
final class SmsDedup {
    private static final long WINDOW_MS = 30_000L;
    private static final int MAX_KEYS = 64;

    private final Map<String, Long> recent = new LinkedHashMap<String, Long>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_KEYS;
        }
    };

    synchronized boolean isDuplicate(String mobile, String content, long deviceTime) {
        String key = (mobile != null ? mobile : "") + "|" + (content != null ? content : "") + "|" + deviceTime;
        long now = System.currentTimeMillis();
        prune(now);
        Long seenAt = recent.get(key);
        if (seenAt != null && now - seenAt < WINDOW_MS) {
            return true;
        }
        recent.put(key, now);
        return false;
    }

    private void prune(long now) {
        Iterator<Map.Entry<String, Long>> it = recent.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (now - e.getValue() > WINDOW_MS) {
                it.remove();
            }
        }
    }
}
