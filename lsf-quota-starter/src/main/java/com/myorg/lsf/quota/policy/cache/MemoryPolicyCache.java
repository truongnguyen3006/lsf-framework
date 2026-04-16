package com.myorg.lsf.quota.policy.cache;

import com.myorg.lsf.quota.policy.QuotaPolicy;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryPolicyCache {

    private final Clock clock;
    private final Duration ttl;
    private final int maxSize;

    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    public MemoryPolicyCache(Clock clock, Duration ttl, int maxSize) {
        this.clock = clock;
        this.ttl = ttl;
        this.maxSize = maxSize;
    }

    public Optional<QuotaPolicy> get(String key) {
        long now = clock.millis();
        Entry e = map.get(key);
        if (e == null) return null; // "no cache"
        if (e.expiresAtMs <= now) {
            map.remove(key);
            return null;
        }
        return e.value; // value may be empty => cached negative
    }

    public void put(String key, Optional<QuotaPolicy> value) {
        long now = clock.millis();
        if (map.size() > maxSize) {
            // cheap eviction: remove random 1
            Iterator<String> it = map.keySet().iterator();
            if (it.hasNext()) map.remove(it.next());
        }
        map.put(key, new Entry(value, now + ttl.toMillis()));
    }

    private record Entry(Optional<QuotaPolicy> value, long expiresAtMs) {}
}