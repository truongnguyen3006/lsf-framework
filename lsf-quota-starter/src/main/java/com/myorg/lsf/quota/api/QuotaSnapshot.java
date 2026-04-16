package com.myorg.lsf.quota.api;

import lombok.Builder;

@Builder
public record QuotaSnapshot(
        String quotaKey,
        int used,
        int reservedCount,
        int confirmedCount,
        long refreshedAtEpochMs
) {
}