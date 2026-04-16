package com.myorg.lsf.quota.api;

public interface QuotaQueryFacade {
    QuotaSnapshot getSnapshot(String quotaKey);
}