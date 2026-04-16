package com.myorg.lsf.quota.policy;

import com.myorg.lsf.quota.config.LsfQuotaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Optional;

@RequiredArgsConstructor
public class JdbcQuotaPolicyProvider implements QuotaPolicyProvider {

    private final JdbcTemplate jdbc;
    private final LsfQuotaProperties props;

    @Override
    public Optional<QuotaPolicy> findPolicy(String quotaKey) {
        String table = props.getProvider().getJdbc().getTable();
        boolean enabledOnly = props.getProvider().getJdbc().isEnabledOnly();

        String sql = enabledOnly
                ? "SELECT quota_limit, hold_seconds FROM " + table + " WHERE quota_key=? AND enabled=1"
                : "SELECT quota_limit, hold_seconds FROM " + table + " WHERE quota_key=?";

        return jdbc.query(sql, rs -> {
            if (!rs.next()) return Optional.empty();

            int limit = rs.getInt("quota_limit");
            Integer holdSeconds = (Integer) rs.getObject("hold_seconds");

            Duration hold = (holdSeconds != null)
                    ? Duration.ofSeconds(holdSeconds)
                    : Duration.ofSeconds(props.getDefaultHoldSeconds());

            return Optional.of(QuotaPolicy.builder()
                    .limit(limit)
                    .hold(hold)
                    .build());
        }, quotaKey);
    }
}