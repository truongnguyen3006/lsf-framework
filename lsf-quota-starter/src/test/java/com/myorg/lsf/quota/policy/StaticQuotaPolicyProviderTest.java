package com.myorg.lsf.quota.policy;

import com.myorg.lsf.quota.config.LsfQuotaProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class StaticQuotaPolicyProviderTest {

    @Test
    void shouldReturnStaticPolicyAndFallbackToDefaultHoldWhenHoldSecondsMissing() {
        LsfQuotaProperties props = new LsfQuotaProperties();
        props.setDefaultHoldSeconds(25);

        LsfQuotaProperties.PolicyItem item = new LsfQuotaProperties.PolicyItem();
        item.setKey("flight:VN123:ECONOMY");
        item.setLimit(4);
        props.getPolicies().add(item);

        StaticQuotaPolicyProvider provider = new StaticQuotaPolicyProvider(props);
        QuotaPolicy policy = provider.findPolicy("flight:VN123:ECONOMY").orElseThrow();

        assertEquals(4, policy.limit());
        assertEquals(Duration.ofSeconds(25), policy.hold());
    }

    @Test
    void shouldReturnEmptyWhenKeyDoesNotExist() {
        StaticQuotaPolicyProvider provider = new StaticQuotaPolicyProvider(new LsfQuotaProperties());
        assertTrue(provider.findPolicy("missing:key").isEmpty());
    }
}
