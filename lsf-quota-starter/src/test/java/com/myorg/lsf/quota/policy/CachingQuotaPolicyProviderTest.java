package com.myorg.lsf.quota.policy;

import com.myorg.lsf.quota.policy.cache.MemoryPolicyCache;
import com.myorg.lsf.quota.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CachingQuotaPolicyProviderTest {

    @Test
    void shouldHitDelegateOnceWhileValueIsStillInMemoryCache() {
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-03-10T08:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        QuotaPolicyProvider delegate = quotaKey -> {
            calls.incrementAndGet();
            return Optional.of(QuotaPolicy.builder().limit(10).hold(Duration.ofSeconds(30)).build());
        };

        MemoryPolicyCache cache = new MemoryPolicyCache(clock, Duration.ofSeconds(5), 100);
        CachingQuotaPolicyProvider provider = new CachingQuotaPolicyProvider(delegate, cache, null);

        assertEquals(10, provider.findPolicy("sku:FLASH-10").orElseThrow().limit());
        assertEquals(10, provider.findPolicy("sku:FLASH-10").orElseThrow().limit());
        assertEquals(1, calls.get());

        clock.advance(Duration.ofSeconds(6));
        assertEquals(10, provider.findPolicy("sku:FLASH-10").orElseThrow().limit());
        assertEquals(2, calls.get());
    }

    @Test
    void shouldCacheNegativeLookupToo() {
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-03-10T08:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        QuotaPolicyProvider delegate = quotaKey -> {
            calls.incrementAndGet();
            return Optional.empty();
        };

        CachingQuotaPolicyProvider provider = new CachingQuotaPolicyProvider(
                delegate,
                new MemoryPolicyCache(clock, Duration.ofSeconds(30), 100),
                null
        );

        assertTrue(provider.findPolicy("missing:key").isEmpty());
        assertTrue(provider.findPolicy("missing:key").isEmpty());
        assertEquals(1, calls.get());
    }
}
