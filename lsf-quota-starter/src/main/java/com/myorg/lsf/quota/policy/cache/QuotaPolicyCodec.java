package com.myorg.lsf.quota.policy.cache;

import com.myorg.lsf.quota.policy.QuotaPolicy;

import java.time.Duration;
import java.util.Optional;

public final class QuotaPolicyCodec {
    private QuotaPolicyCodec() {}

    // format: limit|holdSeconds ; NOT_FOUND encoded as 0|0 with marker "NF"
    public static String encode(Optional<QuotaPolicy> p) {
        if (p.isEmpty()) return "NF";
        QuotaPolicy q = p.get();
        long hs = Math.max(1, q.hold().toSeconds());
        return q.limit() + "|" + hs;
    }

    public static Optional<QuotaPolicy> decode(String s) {
        if (s == null || s.isBlank()) return Optional.empty();
        if ("NF".equals(s)) return Optional.empty();
        String[] parts = s.split("\\|");
        if (parts.length != 2) return Optional.empty();

        int limit = Integer.parseInt(parts[0]);
        long hs = Long.parseLong(parts[1]);
        return Optional.of(QuotaPolicy.builder()
                .limit(limit)
                .hold(Duration.ofSeconds(hs))
                .build());
    }
}