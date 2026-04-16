package com.myorg.lsf.quota.api;

public interface QuotaReservationFacade {
    QuotaResult reserve(String quotaKey, String requestId, int amount);
    QuotaResult confirm(String quotaKey, String requestId);
    QuotaResult release(String quotaKey, String requestId);
}