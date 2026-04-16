package com.myorg.lsf.quota.impl.redis;

import com.myorg.lsf.quota.api.QuotaDecision;
import com.myorg.lsf.quota.api.QuotaRequest;
import com.myorg.lsf.quota.config.LsfQuotaProperties;
import com.myorg.lsf.quota.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_CONCURRENCY_TESTS", matches = "true")
class RedisQuotaServiceConcurrencyTest {

    @Container
    static final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

    @Test
    void concurrentReserveShouldNeverExceedLimit() throws Exception {
        LettuceConnectionFactory cf = new LettuceConnectionFactory(redisContainer.getHost(), redisContainer.getFirstMappedPort());
        cf.afterPropertiesSet();

        try {
            StringRedisTemplate redis = new StringRedisTemplate(cf);
            redis.afterPropertiesSet();

            LsfQuotaProperties props = new LsfQuotaProperties();
            props.setKeyPrefix("test:quota:");
            props.setDefaultHoldSeconds(30);
            props.setKeepAliveSeconds(300);

            MutableClock clock = new MutableClock(
                    Instant.parse("2026-03-10T00:00:00Z"),
                    ZoneOffset.UTC
            );
            RedisQuotaService service = new RedisQuotaService(redis, props, null, clock);

            int limit = 10;
            int workers = 40;
            String quotaKey = "flashsale:sku-01";

            CountDownLatch ready = new CountDownLatch(workers);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(workers);
            List<Future<QuotaDecision>> futures = new ArrayList<>();

            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.reserve(QuotaRequest.builder()
                                    .quotaKey(quotaKey)
                                    .requestId(UUID.randomUUID().toString())
                                    .amount(1)
                                    .limit(limit)
                                    .hold(Duration.ofSeconds(60))
                                    .build())
                            .decision();
                }));
            }

            assertTrue(ready.await(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
            start.countDown();

            int accepted = 0;
            int rejected = 0;
            for (Future<QuotaDecision> future : futures) {
                QuotaDecision decision = future.get();
                if (decision == QuotaDecision.ACCEPTED) accepted++;
                if (decision == QuotaDecision.REJECTED) rejected++;
            }

            pool.shutdown();
            assertEquals(limit, accepted);
            assertEquals(workers - limit, rejected);
        } finally {
            cf.destroy();
        }
    }
}
