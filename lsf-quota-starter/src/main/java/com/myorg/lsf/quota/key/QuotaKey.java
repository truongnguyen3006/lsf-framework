package com.myorg.lsf.quota.key;

import lombok.Builder;

@Builder
public record QuotaKey(
        String tenant,
        String resourceType,
        String resourceId
) {
    public String asString() {
        // tenant:type:id
        return tenant + ":" + resourceType + ":" + resourceId;
    }
}