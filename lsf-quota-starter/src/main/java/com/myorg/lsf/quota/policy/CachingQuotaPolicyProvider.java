package com.myorg.lsf.quota.policy;

import com.myorg.lsf.quota.policy.cache.MemoryPolicyCache;
import com.myorg.lsf.quota.policy.cache.RedisPolicyCache;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class CachingQuotaPolicyProvider implements QuotaPolicyProvider {

    private final QuotaPolicyProvider delegate;
    private final MemoryPolicyCache mem;       // nullable
    private final RedisPolicyCache redis;      // nullable

    @Override
    public Optional<QuotaPolicy> findPolicy(String quotaKey) {
        // 1) memory
        if (mem != null) {
            Optional<QuotaPolicy> v = mem.get(quotaKey);
            if (v != null) return v;
        }

        // 2) redis
        if (redis != null) {
            Optional<QuotaPolicy> v = redis.get(quotaKey);
            if (v != null) {
                if (mem != null) mem.put(quotaKey, v);
                return v;
            }
        }

        // 3) delegate (jdbc/static/custom)
        Optional<QuotaPolicy> v = delegate.findPolicy(quotaKey);

        // 4) fill caches (including negative)
        if (redis != null) redis.put(quotaKey, v);
        if (mem != null) mem.put(quotaKey, v);
        return v;
    }
}