package com.myorg.lsf.quota.policy;

import com.myorg.lsf.quota.config.LsfQuotaProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JdbcQuotaPolicyProviderTest {

    @Test
    void shouldReadOnlyEnabledPolicyWhenEnabledOnlyIsTrue() {
        JdbcTemplate jdbc = new JdbcTemplate(newDataSource("quota_policy_enabled"));
        jdbc.execute("CREATE TABLE quota_policy (quota_key VARCHAR(255) PRIMARY KEY, quota_limit INT, hold_seconds INT, enabled INT)");
        jdbc.update("INSERT INTO quota_policy (quota_key, quota_limit, hold_seconds, enabled) VALUES (?,?,?,?)",
                "course:CT101:GROUP1", 3, 90, 1);
        jdbc.update("INSERT INTO quota_policy (quota_key, quota_limit, hold_seconds, enabled) VALUES (?,?,?,?)",
                "course:CT101:GROUP2", 5, 60, 0);

        LsfQuotaProperties props = new LsfQuotaProperties();
        props.getProvider().getJdbc().setTable("quota_policy");
        props.getProvider().getJdbc().setEnabledOnly(true);

        JdbcQuotaPolicyProvider provider = new JdbcQuotaPolicyProvider(jdbc, props);

        QuotaPolicy enabled = provider.findPolicy("course:CT101:GROUP1").orElseThrow();
        assertEquals(3, enabled.limit());
        assertEquals(Duration.ofSeconds(90), enabled.hold());
        assertTrue(provider.findPolicy("course:CT101:GROUP2").isEmpty());
    }

    @Test
    void shouldFallbackToDefaultHoldWhenDbValueIsNull() {
        JdbcTemplate jdbc = new JdbcTemplate(newDataSource("quota_policy_default_hold"));
        jdbc.execute("CREATE TABLE quota_policy (quota_key VARCHAR(255) PRIMARY KEY, quota_limit INT, hold_seconds INT, enabled INT)");
        jdbc.update("INSERT INTO quota_policy (quota_key, quota_limit, hold_seconds, enabled) VALUES (?,?,?,?)",
                "sku:SKU-1", 8, null, 1);

        LsfQuotaProperties props = new LsfQuotaProperties();
        props.setDefaultHoldSeconds(15);
        props.getProvider().getJdbc().setTable("quota_policy");

        JdbcQuotaPolicyProvider provider = new JdbcQuotaPolicyProvider(jdbc, props);
        QuotaPolicy policy = provider.findPolicy("sku:SKU-1").orElseThrow();

        assertEquals(8, policy.limit());
        assertEquals(Duration.ofSeconds(15), policy.hold());
    }

    private static JdbcDataSource newDataSource(String dbName) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("sa");
        return ds;
    }
}
