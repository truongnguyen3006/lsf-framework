package com.myorg.lsf.quota.config;

import java.util.Objects;

/**
 * Fail-fast validation for quota starter properties.
 *
 * The goal is to reject broken configuration early at startup instead of
 * allowing subtle runtime bugs such as negative TTLs, blank keys or invalid
 * policy entries.
 */
public final class QuotaConfigurationValidator {

    private QuotaConfigurationValidator() {
    }

    public static void validate(LsfQuotaProperties props) {
        Objects.requireNonNull(props, "LsfQuotaProperties must not be null");

        requirePositive(props.getDefaultHoldSeconds(), "lsf.quota.default-hold-seconds must be > 0");
        requirePositive(props.getKeepAliveSeconds(), "lsf.quota.keep-alive-seconds must be > 0");
        requireNonBlank(props.getKeyPrefix(), "lsf.quota.key-prefix must not be blank");

        var provider = props.getProvider();
        if (provider == null) {
            throw new IllegalStateException("lsf.quota.provider must not be null");
        }

        if (provider.getJdbc() == null) {
            throw new IllegalStateException("lsf.quota.provider.jdbc must not be null");
        }
        requireNonBlank(provider.getJdbc().getTable(), "lsf.quota.provider.jdbc.table must not be blank");

        if (provider.getCache() == null) {
            throw new IllegalStateException("lsf.quota.provider.cache must not be null");
        }
        requirePositive(provider.getCache().getTtlSeconds(), "lsf.quota.provider.cache.ttl-seconds must be > 0");
        requirePositive(provider.getCache().getLocalMaxSize(), "lsf.quota.provider.cache.local-max-size must be > 0");
        requireNonBlank(provider.getCache().getRedisPrefix(), "lsf.quota.provider.cache.redis-prefix must not be blank");

        for (int i = 0; i < props.getPolicies().size(); i++) {
            var item = props.getPolicies().get(i);
            if (item == null) {
                throw new IllegalStateException("lsf.quota.policies[" + i + "] must not be null");
            }
            requireNonBlank(item.getKey(), "lsf.quota.policies[" + i + "].key must not be blank");
            requirePositive(item.getLimit(), "lsf.quota.policies[" + i + "].limit must be > 0");
            if (item.getHoldSeconds() != null && item.getHoldSeconds() <= 0) {
                throw new IllegalStateException("lsf.quota.policies[" + i + "].hold-seconds must be > 0 when provided");
            }
        }
    }

    private static void requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}
