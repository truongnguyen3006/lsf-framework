package com.myorg.lsf.quota.policy;

import com.myorg.lsf.quota.config.LsfQuotaProperties;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Optional;

@RequiredArgsConstructor
public class StaticQuotaPolicyProvider implements QuotaPolicyProvider {

    private final LsfQuotaProperties props;

    @Override
    public Optional<QuotaPolicy> findPolicy(String quotaKey) {
        for (LsfQuotaProperties.PolicyItem item : props.getPolicies()) {
            if (quotaKey.equals(item.getKey())) {
                Duration hold = (item.getHoldSeconds() != null)
                        ? Duration.ofSeconds(item.getHoldSeconds())
                        : Duration.ofSeconds(props.getDefaultHoldSeconds());

                return Optional.of(QuotaPolicy.builder()
                        .limit(item.getLimit())
                        .hold(hold)
                        .build());
            }
        }
        return Optional.empty();
    }
}