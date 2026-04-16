package com.myorg.lsf.quota.policy.cache;

import com.myorg.lsf.quota.policy.QuotaPolicy;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisPolicyCache {

    private final StringRedisTemplate redis;
    private final String prefix;
    private final Duration ttl;

    public RedisPolicyCache(StringRedisTemplate redis, String prefix, Duration ttl) {
        this.redis = redis;
        this.prefix = prefix;
        this.ttl = ttl;
    }

    public Optional<QuotaPolicy> get(String key) {
        String v = redis.opsForValue().get(prefix + key);
        if (v == null) return null; // "no cache"
        return QuotaPolicyCodec.decode(v);
    }

    public void put(String key, Optional<QuotaPolicy> value) {
        String encoded = QuotaPolicyCodec.encode(value);
        redis.opsForValue().set(prefix + key, encoded, ttl);
    }
}