package com.myorg.lsf.quota.autoconfig;

import com.myorg.lsf.quota.api.QuotaReservationFacade;
import com.myorg.lsf.quota.api.QuotaService;
import com.myorg.lsf.quota.config.LsfQuotaProperties;
import com.myorg.lsf.quota.config.QuotaConfigurationValidator;
import com.myorg.lsf.quota.impl.QuotaReservationFacadeImpl;
import com.myorg.lsf.quota.impl.memory.MemoryQuotaService;
import com.myorg.lsf.quota.impl.redis.RedisQuotaService;
import com.myorg.lsf.quota.obs.QuotaMetrics;
import com.myorg.lsf.quota.policy.CachingQuotaPolicyProvider;
import com.myorg.lsf.quota.policy.JdbcQuotaPolicyProvider;
import com.myorg.lsf.quota.policy.QuotaPolicyProvider;
import com.myorg.lsf.quota.policy.StaticQuotaPolicyProvider;
import com.myorg.lsf.quota.policy.cache.MemoryPolicyCache;
import com.myorg.lsf.quota.policy.cache.RedisPolicyCache;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import com.myorg.lsf.quota.api.QuotaQueryFacade;

import java.time.Clock;
import java.time.Duration;

@AutoConfiguration
@EnableConfigurationProperties(LsfQuotaProperties.class)
@ConditionalOnProperty(prefix = "lsf.quota", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LsfQuotaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "lsfQuotaClock")
    public Clock lsfQuotaClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public QuotaService quotaService(
            LsfQuotaProperties props,
            ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            Environment env,
            @Qualifier("lsfQuotaClock") Clock clock
    ) {
        QuotaConfigurationValidator.validate(props);

        String app = env.getProperty("spring.application.name", "unknown-service");
        String backend = props.getStore().name().toLowerCase();

        QuotaMetrics metrics = null;
        MeterRegistry r = meterRegistryProvider.getIfAvailable();
        if (props.isMetricsEnabled() && r != null) {
            metrics = new QuotaMetrics(r, app, backend);
        }

        return switch (props.getStore()) {
            case REDIS -> {
                StringRedisTemplate redis = redisProvider.getIfAvailable();
                if (redis == null) {
                    throw new IllegalStateException("lsf.quota.store=redis but no StringRedisTemplate found. Add spring-boot-starter-data-redis.");
                }
                yield new RedisQuotaService(redis, props, metrics, clock);
            }
            case MEMORY -> new MemoryQuotaService(props, metrics, clock);
            case AUTO -> {
                StringRedisTemplate redis = redisProvider.getIfAvailable();
                if (redis != null) yield new RedisQuotaService(redis, props, metrics, clock);
                yield new MemoryQuotaService(props, metrics, clock);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public QuotaQueryFacade quotaQueryFacade(@Qualifier("quotaService") QuotaService quotaService) {
        if (quotaService instanceof QuotaQueryFacade queryFacade) {
            return queryFacade::getSnapshot;
        }
        throw new IllegalStateException("Configured QuotaService does not support quota snapshot queries.");
    }

    @Bean
    @ConditionalOnMissingBean
    public QuotaReservationFacade quotaReservationFacade(@Qualifier("quotaService") QuotaService quotaService, QuotaPolicyProvider policyProvider) {
        return new QuotaReservationFacadeImpl(quotaService, policyProvider);
    }

    @Bean(name = "lsfQuotaPolicyProviderBase")
    @ConditionalOnMissingBean(name = "lsfQuotaPolicyProviderBase")
    public QuotaPolicyProvider lsfQuotaPolicyProviderBase(
            LsfQuotaProperties props,
            ObjectProvider<JdbcTemplate> jdbcProvider
    ) {
        QuotaConfigurationValidator.validate(props);
        var mode = props.getProvider().getMode();

        return switch (mode) {
            case JDBC -> {
                JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
                if (jdbc == null) throw new IllegalStateException("lsf.quota.provider.mode=JDBC but no JdbcTemplate found. Add spring-boot-starter-jdbc.");
                yield new JdbcQuotaPolicyProvider(jdbc, props);
            }
            case STATIC -> new StaticQuotaPolicyProvider(props);
            case AUTO -> {
                JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
                if (jdbc != null) yield new JdbcQuotaPolicyProvider(jdbc, props);
                yield new StaticQuotaPolicyProvider(props);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(QuotaPolicyProvider.class)
    public QuotaPolicyProvider quotaPolicyProvider(
            LsfQuotaProperties props,
            @Qualifier("lsfQuotaPolicyProviderBase") QuotaPolicyProvider base,
            ObjectProvider<StringRedisTemplate> redisProvider,
            @Qualifier("lsfQuotaClock") Clock clock
    ) {
        QuotaConfigurationValidator.validate(props);
        var cacheCfg = props.getProvider().getCache();
        var mode = cacheCfg.getMode();
        if (mode == LsfQuotaProperties.PolicyProvider.CacheMode.NONE) {
            return base;
        }

        Duration ttl = Duration.ofSeconds(Math.max(1, cacheCfg.getTtlSeconds()));

        MemoryPolicyCache mem = null;
        RedisPolicyCache redis = null;

        if (mode == LsfQuotaProperties.PolicyProvider.CacheMode.MEMORY
                || mode == LsfQuotaProperties.PolicyProvider.CacheMode.MEMORY_REDIS) {
            mem = new MemoryPolicyCache(clock, ttl, cacheCfg.getLocalMaxSize());
        }

        if (mode == LsfQuotaProperties.PolicyProvider.CacheMode.REDIS
                || mode == LsfQuotaProperties.PolicyProvider.CacheMode.MEMORY_REDIS) {
            StringRedisTemplate rt = redisProvider.getIfAvailable();
            if (rt == null) {
                throw new IllegalStateException("lsf.quota.provider.cache.mode requires Redis but no StringRedisTemplate found. Add spring-boot-starter-data-redis.");
            }
            redis = new RedisPolicyCache(rt, cacheCfg.getRedisPrefix(), ttl);
        }

        return new CachingQuotaPolicyProvider(base, mem, redis);
    }
}