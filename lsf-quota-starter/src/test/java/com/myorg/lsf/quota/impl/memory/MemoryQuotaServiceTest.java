package com.myorg.lsf.quota.impl.memory;

import com.myorg.lsf.quota.api.QuotaDecision;
import com.myorg.lsf.quota.api.QuotaRequest;
import com.myorg.lsf.quota.api.QuotaResult;
import com.myorg.lsf.quota.api.QuotaState;
import com.myorg.lsf.quota.config.LsfQuotaProperties;
import com.myorg.lsf.quota.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class MemoryQuotaServiceTest {

    @Test
    void reserveDuplicateConfirmAndReleaseFlowShouldBeConsistent() {
        LsfQuotaProperties props = baseProps();
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-03-10T08:00:00Z"));
        MemoryQuotaService service = new MemoryQuotaService(props, null, clock);

        QuotaRequest request = QuotaRequest.builder()
                .quotaKey("sku:FLASH-1")
                .requestId("REQ-1")
                .amount(2)
                .limit(5)
                .hold(Duration.ofSeconds(30))
                .build();

        QuotaResult accepted = service.reserve(request);
        assertEquals(QuotaDecision.ACCEPTED, accepted.decision());
        assertEquals(QuotaState.RESERVED, accepted.state());
        assertEquals(2, accepted.used());
        assertTrue(accepted.holdUntilEpochMs() > 0);

        QuotaResult duplicate = service.reserve(request);
        assertEquals(QuotaDecision.DUPLICATE, duplicate.decision());
        assertEquals(QuotaState.RESERVED, duplicate.state());
        assertEquals(2, duplicate.used());

        QuotaResult confirmed = service.confirm("sku:FLASH-1", "REQ-1");
        assertEquals(QuotaDecision.ACCEPTED, confirmed.decision());
        assertEquals(QuotaState.CONFIRMED, confirmed.state());
        assertEquals(2, confirmed.used());

        QuotaResult duplicateConfirm = service.confirm("sku:FLASH-1", "REQ-1");
        assertEquals(QuotaDecision.DUPLICATE, duplicateConfirm.decision());
        assertEquals(QuotaState.CONFIRMED, duplicateConfirm.state());

        QuotaResult rejectedRelease = service.release("sku:FLASH-1", "REQ-1");
        assertEquals(QuotaDecision.NOT_FOUND, rejectedRelease.decision());
        assertEquals(2, rejectedRelease.used());
    }

    @Test
    void expiredReservationShouldFreeCapacityForNextRequest() {
        LsfQuotaProperties props = baseProps();
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-03-10T08:00:00Z"));
        MemoryQuotaService service = new MemoryQuotaService(props, null, clock);

        QuotaRequest first = QuotaRequest.builder()
                .quotaKey("course:CT101")
                .requestId("REQ-A")
                .amount(3)
                .limit(3)
                .hold(Duration.ofSeconds(5))
                .build();

        assertEquals(QuotaDecision.ACCEPTED, service.reserve(first).decision());

        QuotaRequest second = QuotaRequest.builder()
                .quotaKey("course:CT101")
                .requestId("REQ-B")
                .amount(1)
                .limit(3)
                .hold(Duration.ofSeconds(5))
                .build();

        assertEquals(QuotaDecision.REJECTED, service.reserve(second).decision());

        clock.advance(Duration.ofSeconds(6));
        QuotaResult afterExpiry = service.reserve(second);
        assertEquals(QuotaDecision.ACCEPTED, afterExpiry.decision());
        assertEquals(1, afterExpiry.used());
        assertEquals(2, afterExpiry.remaining());
    }

    @Test
    void releaseConfirmedShouldDependOnConfiguration() {
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-03-10T08:00:00Z"));

        LsfQuotaProperties disallowProps = baseProps();
        disallowProps.setAllowReleaseConfirmed(false);
        MemoryQuotaService disallowService = new MemoryQuotaService(disallowProps, null, clock);

        QuotaRequest request = QuotaRequest.builder()
                .quotaKey("ticket:VIP")
                .requestId("REQ-77")
                .amount(1)
                .limit(1)
                .hold(Duration.ofSeconds(30))
                .build();

        disallowService.reserve(request);
        disallowService.confirm("ticket:VIP", "REQ-77");
        QuotaResult notAllowed = disallowService.release("ticket:VIP", "REQ-77");
        assertEquals(QuotaDecision.NOT_FOUND, notAllowed.decision());
        assertEquals(1, notAllowed.used());

        LsfQuotaProperties allowProps = baseProps();
        allowProps.setAllowReleaseConfirmed(true);
        MemoryQuotaService allowService = new MemoryQuotaService(allowProps, null, clock);
        allowService.reserve(request);
        allowService.confirm("ticket:VIP", "REQ-77");

        QuotaResult released = allowService.release("ticket:VIP", "REQ-77");
        assertEquals(QuotaDecision.ACCEPTED, released.decision());
        assertEquals(0, released.used());
    }

    @Test
    void concurrentReserveShouldNeverExceedLimit() throws Exception {
        LsfQuotaProperties props = baseProps();
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-03-10T08:00:00Z"));
        MemoryQuotaService service = new MemoryQuotaService(props, null, clock);

        int limit = 10;
        int workers = 40;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<QuotaResult>> futures = new ArrayList<>();

        for (int i = 0; i < workers; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return service.reserve(QuotaRequest.builder()
                        .quotaKey("sku:HOT")
                        .requestId("REQ-" + idx)
                        .amount(1)
                        .limit(limit)
                        .hold(Duration.ofSeconds(60))
                        .build());
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        int accepted = 0;
        for (Future<QuotaResult> future : futures) {
            QuotaResult result = future.get(5, TimeUnit.SECONDS);
            if (result.decision() == QuotaDecision.ACCEPTED) {
                accepted++;
            }
        }
        executor.shutdownNow();

        assertEquals(limit, accepted);
        QuotaResult extra = service.reserve(QuotaRequest.builder()
                .quotaKey("sku:HOT")
                .requestId("REQ-extra")
                .amount(1)
                .limit(limit)
                .hold(Duration.ofSeconds(60))
                .build());
        assertEquals(QuotaDecision.REJECTED, extra.decision());
        assertEquals(limit, extra.used());
    }

    private static LsfQuotaProperties baseProps() {
        LsfQuotaProperties props = new LsfQuotaProperties();
        props.setDefaultHoldSeconds(30);
        props.setKeepAliveSeconds(60);
        return props;
    }
}
