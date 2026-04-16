package com.myorg.lsf.quota.impl;

import com.myorg.lsf.quota.api.QuotaReservationFacade;
import com.myorg.lsf.quota.api.QuotaResult;
import com.myorg.lsf.quota.api.QuotaService;
import com.myorg.lsf.quota.api.QuotaRequest;
import com.myorg.lsf.quota.api.QuotaPolicyNotFoundException;
import com.myorg.lsf.quota.policy.QuotaPolicy;
import com.myorg.lsf.quota.policy.QuotaPolicyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class QuotaReservationFacadeImpl implements QuotaReservationFacade {

    private final QuotaService quotaService;
    private final QuotaPolicyProvider policyProvider;

    @Override
    public QuotaResult reserve(String quotaKey, String requestId, int amount) {
        requireNonBlank(quotaKey, "quotaKey must not be blank");
        requireNonBlank(requestId, "requestId must not be blank");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }

        QuotaPolicy policy = policyProvider.findPolicy(quotaKey)
                .orElseThrow(() -> new QuotaPolicyNotFoundException(quotaKey));

        QuotaResult result = quotaService.reserve(QuotaRequest.builder()
                .quotaKey(quotaKey)
                .requestId(requestId)
                .amount(amount)
                .limit(policy.limit())
                .hold(policy.hold())
                .build());

        log.debug("quota.reserve key={} requestId={} amount={} decision={} used={} limit={} holdUntil={}",
                quotaKey, requestId, amount, result.decision(), result.used(), result.limit(), result.holdUntilEpochMs());
        return result;
    }

    @Override
    public QuotaResult confirm(String quotaKey, String requestId) {
        requireNonBlank(quotaKey, "quotaKey must not be blank");
        requireNonBlank(requestId, "requestId must not be blank");

        QuotaResult result = quotaService.confirm(quotaKey, requestId);
        log.debug("quota.confirm key={} requestId={} decision={} used={} state={}",
                quotaKey, requestId, result.decision(), result.used(), result.state());
        return result;
    }

    @Override
    public QuotaResult release(String quotaKey, String requestId) {
        requireNonBlank(quotaKey, "quotaKey must not be blank");
        requireNonBlank(requestId, "requestId must not be blank");

        QuotaResult result = quotaService.release(quotaKey, requestId);
        log.debug("quota.release key={} requestId={} decision={} used={} state={}",
                quotaKey, requestId, result.decision(), result.used(), result.state());
        return result;
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}