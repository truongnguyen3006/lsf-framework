package com.myorg.lsf.quota.policy;

import java.util.Optional;

public interface QuotaPolicyProvider {
    Optional<QuotaPolicy> findPolicy(String quotaKey);
}
