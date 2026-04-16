package com.myorg.lsf.quota.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuotaConfigurationValidatorTest {

    @Test
    void shouldAcceptSaneDefaults() {
        LsfQuotaProperties props = new LsfQuotaProperties();
        assertDoesNotThrow(() -> QuotaConfigurationValidator.validate(props));
    }

    @Test
    void shouldRejectBlankKeyPrefix() {
        LsfQuotaProperties props = new LsfQuotaProperties();
        props.setKeyPrefix("  ");

        assertThrows(IllegalStateException.class, () -> QuotaConfigurationValidator.validate(props));
    }

    @Test
    void shouldRejectInvalidStaticPolicy() {
        LsfQuotaProperties props = new LsfQuotaProperties();
        LsfQuotaProperties.PolicyItem item = new LsfQuotaProperties.PolicyItem();
        item.setKey("demo:sku:1");
        item.setLimit(0);
        props.getPolicies().add(item);

        assertThrows(IllegalStateException.class, () -> QuotaConfigurationValidator.validate(props));
    }

    @Test
    void shouldRejectInvalidCacheTtl() {
        LsfQuotaProperties props = new LsfQuotaProperties();
        props.getProvider().getCache().setTtlSeconds(0);

        assertThrows(IllegalStateException.class, () -> QuotaConfigurationValidator.validate(props));
    }
}
