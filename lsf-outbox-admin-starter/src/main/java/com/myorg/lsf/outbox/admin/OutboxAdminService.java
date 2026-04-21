package com.myorg.lsf.outbox.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class OutboxAdminService {

    private final JdbcOutboxAdminRepository repo;
    private final LsfOutboxAdminProperties props;
    private final Clock clock;

    public List<OutboxAdminRow> list(List<OutboxStatus> statuses,
                                     String topic,
                                     String msgKey,
                                     String eventType,
                                     String correlationId,
                                     Instant from,
                                     Instant to,
                                     Integer limit,
                                     Integer offset) {
        int lim = clamp(limit != null ? limit : props.getDefaultLimit());
        int off = Math.max(0, offset != null ? offset : 0);
        return repo.list(
                statuses,
                topic,
                normalize(msgKey),
                normalize(eventType),
                normalize(correlationId),
                from,
                to,
                lim,
                off
        );
    }

    public Optional<OutboxAdminRow> findByEventId(String eventId) {
        return repo.findByEventId(eventId);
    }

    public Optional<OutboxAdminRow> findById(long id) {
        return repo.findById(id);
    }

    @Transactional
    public int requeueByEventId(String eventId, OutboxStatus mode, boolean resetRetry) {
        if (!props.isAllowRetry()) {
            throw new IllegalStateException("Retry is disabled. Set lsf.outbox.admin.allow-retry=true to enable.");
        }
        Instant now = clock.instant();
        return repo.requeueByEventId(eventId, mode, resetRetry, now);
    }

    @Transactional
    public int requeueFailed(Integer limit, boolean resetRetry) {
        if (!props.isAllowRetry()) {
            throw new IllegalStateException("Retry is disabled. Set lsf.outbox.admin.allow-retry=true to enable.");
        }
        int lim = clamp(limit != null ? limit : props.getDefaultLimit());
        Instant now = clock.instant();
        return repo.requeueFailed(lim, resetRetry, now);
    }

    @Transactional
    public int markFailedByEventId(String eventId, String error) {
        return repo.markFailedByEventId(eventId, error);
    }

    @Transactional
    public int deleteByEventId(String eventId) {
        if (!props.isAllowDelete()) {
            throw new IllegalStateException("Delete is disabled. Set lsf.outbox.admin.allow-delete=true to enable.");
        }
        return repo.deleteByEventId(eventId);
    }

    private int clamp(int v) {
        if (v <= 0) return props.getDefaultLimit();
        return Math.min(v, props.getMaxLimit());
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
