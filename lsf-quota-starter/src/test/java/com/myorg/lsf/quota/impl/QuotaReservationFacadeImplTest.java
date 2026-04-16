package com.myorg.lsf.quota.impl;

import com.myorg.lsf.quota.api.QuotaDecision;
import com.myorg.lsf.quota.api.QuotaRequest;
import com.myorg.lsf.quota.api.QuotaResult;
import com.myorg.lsf.quota.api.QuotaService;
import com.myorg.lsf.quota.policy.QuotaPolicy;
import com.myorg.lsf.quota.policy.QuotaPolicyProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QuotaReservationFacadeImplTest {

    @Test
    void reserveShouldResolvePolicyAndPassLimitAndHoldToBackend() {
        RecordingQuotaService quotaService = new RecordingQuotaService();
        QuotaPolicyProvider provider = quotaKey -> Optional.of(QuotaPolicy.builder()
                .limit(7)
                .hold(Duration.ofSeconds(45))
                .build());

        QuotaReservationFacadeImpl facade = new QuotaReservationFacadeImpl(quotaService, provider);

        QuotaResult result = facade.reserve("course:CT555", "REQ-1", 2);

        assertEquals(QuotaDecision.ACCEPTED, result.decision());
        assertNotNull(quotaService.lastRequest);
        assertEquals("course:CT555", quotaService.lastRequest.quotaKey());
        assertEquals("REQ-1", quotaService.lastRequest.requestId());
        assertEquals(2, quotaService.lastRequest.amount());
        assertEquals(7, quotaService.lastRequest.limit());
        assertEquals(Duration.ofSeconds(45), quotaService.lastRequest.hold());
    }

    @Test
    void reserveShouldFailFastWhenNoPolicyExists() {
        QuotaReservationFacadeImpl facade = new QuotaReservationFacadeImpl(new RecordingQuotaService(), quotaKey -> Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> facade.reserve("missing:key", "REQ-404", 1));

        assertTrue(ex.getMessage().contains("No quota policy"));
    }

    private static final class RecordingQuotaService implements QuotaService {
        private QuotaRequest lastRequest;

        @Override
        public QuotaResult reserve(QuotaRequest req) {
            this.lastRequest = req;
            return QuotaResult.builder().decision(QuotaDecision.ACCEPTED).limit(req.limit()).used(req.amount()).build();
        }

        @Override
        public QuotaResult confirm(String quotaKey, String requestId) {
            return QuotaResult.builder().decision(QuotaDecision.ACCEPTED).build();
        }

        @Override
        public QuotaResult release(String quotaKey, String requestId) {
            return QuotaResult.builder().decision(QuotaDecision.ACCEPTED).build();
        }
    }
}
