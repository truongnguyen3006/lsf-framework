package com.myorg.lsf.quota.autoconfig;

import com.myorg.lsf.quota.policy.QuotaPolicyProvider;
import com.myorg.lsf.quota.api.QuotaReservationFacade;
import com.myorg.lsf.quota.api.QuotaService;
import com.myorg.lsf.quota.impl.memory.MemoryQuotaService;
import com.myorg.lsf.quota.policy.StaticQuotaPolicyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class QuotaAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    LsfQuotaAutoConfiguration.class
            ));

    @Test
    void shouldCreateMemoryBackendWhenConfiguredExplicitly() {
        contextRunner
                .withPropertyValues(
                        "lsf.quota.enabled=true",
                        "lsf.quota.store=memory",
                        "lsf.quota.provider.mode=static"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(QuotaService.class);
                    assertThat(context.getBean(QuotaService.class)).isInstanceOf(MemoryQuotaService.class);
                    assertThat(context).hasSingleBean(QuotaReservationFacade.class);
                });
    }

    @Test
    void autoModeShouldFallbackToMemoryWhenRedisIsAbsent() {
        contextRunner
                .withPropertyValues(
                        "lsf.quota.store=auto",
                        "lsf.quota.provider.mode=static"
                )
                .run(context -> assertThat(context.getBean(QuotaService.class)).isInstanceOf(MemoryQuotaService.class));
    }

    @Test
    void shouldFailFastWhenRedisModeIsForcedButRedisTemplateMissing() {
        contextRunner
                .withPropertyValues(
                        "lsf.quota.store=redis",
                        "lsf.quota.provider.mode=static"
                )
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("lsf.quota.store=redis")
                            .hasMessageContaining("StringRedisTemplate");
                });
    }

    @Test
    void shouldFailFastWhenJdbcProviderIsForcedButJdbcTemplateMissing() {
        contextRunner
                .withPropertyValues(
                        "lsf.quota.store=memory",
                        "lsf.quota.provider.mode=jdbc"
                )
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("lsf.quota.provider.mode=JDBC")
                            .hasMessageContaining("JdbcTemplate");
                });
    }

    @Test
    void shouldBuildStaticPolicyProviderFromConfiguration() {
        contextRunner
                .withPropertyValues(
                        "lsf.quota.store=memory",
                        "lsf.quota.provider.mode=static",
                        "lsf.quota.provider.cache.mode=none",
                        "lsf.quota.default-hold-seconds=20",
                        "lsf.quota.policies[0].key=uniA:course:CT555-NHOM1",
                        "lsf.quota.policies[0].limit=2"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(QuotaPolicyProvider.class);
                    assertThat(context.getBean(QuotaPolicyProvider.class)).isInstanceOf(StaticQuotaPolicyProvider.class);
                });
    }
}
