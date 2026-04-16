package com.myorg.lsf.quota.impl.redis;

import com.myorg.lsf.quota.api.QuotaDecision;
import com.myorg.lsf.quota.api.QuotaRequest;
import com.myorg.lsf.quota.config.LsfQuotaProperties;
import com.myorg.lsf.quota.support.MutableClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class RedisQuotaServiceTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void beforeAll() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void afterAll() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldHandleReserveDuplicateConfirmAndReleaseConfirmedFlow() {
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-03-10T08:00:00Z"));
        RedisQuotaService service = new RedisQuotaService(redisTemplate, props(true), null, clock);
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        QuotaRequest request = QuotaRequest.builder()
                .quotaKey("ticket:LIVE-1")
                .requestId("REQ-1")
                .amount(1)
                .limit(2)
                .hold(Duration.ofSeconds(30))
                .build();

        assertEquals(QuotaDecision.ACCEPTED, service.reserve(request).decision());
        assertEquals(QuotaDecision.DUPLICATE, service.reserve(request).decision());
        assertEquals(QuotaDecision.ACCEPTED, service.confirm("ticket:LIVE-1", "REQ-1").decision());
        assertEquals(QuotaDecision.DUPLICATE, service.confirm("ticket:LIVE-1", "REQ-1").decision());
        assertEquals(QuotaDecision.ACCEPTED, service.release("ticket:LIVE-1", "REQ-1").decision());
        assertEquals(QuotaDecision.NOT_FOUND, service.release("ticket:LIVE-1", "REQ-1").decision());
    }

    @Test
    void expiredReservationShouldBePurgedInRedisScript() {
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-03-10T08:00:00Z"));
        RedisQuotaService service = new RedisQuotaService(redisTemplate, props(false), null, clock);
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        assertEquals(QuotaDecision.ACCEPTED, service.reserve(QuotaRequest.builder()
                .quotaKey("sku:LIMIT-1")
                .requestId("REQ-A")
                .amount(2)
                .limit(2)
                .hold(Duration.ofSeconds(5))
                .build()).decision());

        assertEquals(QuotaDecision.REJECTED, service.reserve(QuotaRequest.builder()
                .quotaKey("sku:LIMIT-1")
                .requestId("REQ-B")
                .amount(1)
                .limit(2)
                .hold(Duration.ofSeconds(5))
                .build()).decision());

        clock.advance(Duration.ofSeconds(6));
        assertEquals(QuotaDecision.ACCEPTED, service.reserve(QuotaRequest.builder()
                .quotaKey("sku:LIMIT-1")
                .requestId("REQ-B")
                .amount(1)
                .limit(2)
                .hold(Duration.ofSeconds(5))
                .build()).decision());
    }

    private static LsfQuotaProperties props(boolean allowReleaseConfirmed) {
        LsfQuotaProperties props = new LsfQuotaProperties();
        props.setKeyPrefix("lsf:test:quota:");
        props.setDefaultHoldSeconds(30);
        props.setKeepAliveSeconds(120);
        props.setAllowReleaseConfirmed(allowReleaseConfirmed);
        return props;
    }
}
