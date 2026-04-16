package com.myorg.lsf.quota.api;

public interface QuotaService {
    QuotaResult reserve(QuotaRequest req);
    QuotaResult confirm(String quotaKey, String requestId);
    QuotaResult release(String quotaKey, String requestId);
}
