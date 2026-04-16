package com.myorg.lsf.quota.policy;

import lombok.Builder;

import java.time.Duration;

@Builder
public record QuotaPolicy (
        int limit,
        Duration hold
){
}
