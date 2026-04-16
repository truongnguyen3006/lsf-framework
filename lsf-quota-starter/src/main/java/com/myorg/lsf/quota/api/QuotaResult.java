package com.myorg.lsf.quota.api;

import lombok.Builder;

@Builder
public record QuotaResult(
        QuotaDecision decision,
        QuotaState state,
        int used,
        int limit,
        long holdUntilEpochMs
) {
    // tính toán số lượng tồn kho
    public int remaining(){
        // vì sợ used vô tình vượt quá limit, nên phải max(0,...)
        return Math.max(0, limit - used);
    }
}
