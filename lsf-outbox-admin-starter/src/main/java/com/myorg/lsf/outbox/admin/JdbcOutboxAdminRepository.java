package com.myorg.lsf.outbox.admin;

import com.myorg.lsf.outbox.sql.OutboxSql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public class JdbcOutboxAdminRepository {

    private final NamedParameterJdbcTemplate named;
    private final JdbcTemplate jdbc;
    private final String table;

    private String t() {
        return OutboxSql.validateTableName(table);
    }

    public List<OutboxAdminRow> list(List<OutboxStatus> statuses,
                                     String topic,
                                     String msgKey,
                                     String eventType,
                                     String correlationId,
                                     Instant from,
                                     Instant to,
                                     int limit,
                                     int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, topic, msg_key, event_id, event_type, correlation_id, aggregate_id, status,
                   created_at, sent_at, retry_count, last_error,
                   lease_owner, lease_until, next_attempt_at
            FROM %s
            WHERE 1=1
            """.formatted(t()));

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);

        if (statuses != null && !statuses.isEmpty()) {
            sql.append(" AND status IN (:statuses) ");
            p.addValue("statuses", statuses.stream().map(Enum::name).toList());
        }

        if (topic != null && !topic.isBlank()) {
            sql.append(" AND topic = :topic ");
            p.addValue("topic", topic.trim());
        }

        if (msgKey != null && !msgKey.isBlank()) {
            sql.append(" AND msg_key = :msgKey ");
            p.addValue("msgKey", msgKey.trim());
        }

        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND event_type = :eventType ");
            p.addValue("eventType", eventType.trim());
        }

        if (correlationId != null && !correlationId.isBlank()) {
            sql.append(" AND correlation_id = :correlationId ");
            p.addValue("correlationId", correlationId.trim());
        }

        if (from != null) {
            sql.append(" AND created_at >= :from ");
            p.addValue("from", Timestamp.from(from));
        }

        if (to != null) {
            sql.append(" AND created_at <= :to ");
            p.addValue("to", Timestamp.from(to));
        }

        sql.append(" ORDER BY id DESC LIMIT :limit OFFSET :offset ");

        return named.query(sql.toString(), p, (rs, i) -> new OutboxAdminRow(
                rs.getLong("id"),
                rs.getString("topic"),
                rs.getString("msg_key"),
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("correlation_id"),
                rs.getString("aggregate_id"),
                OutboxStatus.from(rs.getString("status")),
                rs.getInt("retry_count"),
                tsToInstant(rs.getTimestamp("created_at")),
                tsToInstant(rs.getTimestamp("sent_at")),
                tsToInstant(rs.getTimestamp("next_attempt_at")),
                tsToInstant(rs.getTimestamp("lease_until")),
                rs.getString("lease_owner"),
                rs.getString("last_error")
        ));
    }

    public Optional<OutboxAdminRow> findByEventId(String eventId) {
        String sql = """
                SELECT id, topic, msg_key, event_id, event_type, correlation_id, aggregate_id, status,
                       created_at, sent_at, retry_count, last_error,
                       lease_owner, lease_until, next_attempt_at
                FROM %s
                WHERE event_id = ?
                """.formatted(t());

        List<OutboxAdminRow> rows = jdbc.query(sql, (rs, i) -> new OutboxAdminRow(
                rs.getLong("id"),
                rs.getString("topic"),
                rs.getString("msg_key"),
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("correlation_id"),
                rs.getString("aggregate_id"),
                OutboxStatus.from(rs.getString("status")),
                rs.getInt("retry_count"),
                tsToInstant(rs.getTimestamp("created_at")),
                tsToInstant(rs.getTimestamp("sent_at")),
                tsToInstant(rs.getTimestamp("next_attempt_at")),
                tsToInstant(rs.getTimestamp("lease_until")),
                rs.getString("lease_owner"),
                rs.getString("last_error")
        ), eventId);

        return rows.stream().findFirst();
    }

    public Optional<OutboxAdminRow> findById(long id) {
        String sql = """
            SELECT id, topic, msg_key, event_id, event_type, correlation_id, aggregate_id, status,
                   created_at, sent_at, retry_count, last_error,
                   lease_owner, lease_until, next_attempt_at
            FROM %s
            WHERE id = ?
            """.formatted(t());

        List<OutboxAdminRow> rows = jdbc.query(sql, (rs, i) -> new OutboxAdminRow(
                rs.getLong("id"),
                rs.getString("topic"),
                rs.getString("msg_key"),
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("correlation_id"),
                rs.getString("aggregate_id"),
                OutboxStatus.from(rs.getString("status")),
                rs.getInt("retry_count"),
                tsToInstant(rs.getTimestamp("created_at")),
                tsToInstant(rs.getTimestamp("sent_at")),
                tsToInstant(rs.getTimestamp("next_attempt_at")),
                tsToInstant(rs.getTimestamp("lease_until")),
                rs.getString("lease_owner"),
                rs.getString("last_error")
        ), id);

        return rows.stream().findFirst();
    }

    /** mode=NEW => chạy ngay, mode=RETRY => set next_attempt_at=now */
    public int requeueByEventId(String eventId, OutboxStatus mode, boolean resetRetry, Instant now) {
        if (mode != OutboxStatus.NEW && mode != OutboxStatus.RETRY) {
            throw new IllegalArgumentException("mode must be NEW or RETRY");
        }

        String sql = """
                UPDATE %s
                SET status = :status,
                    next_attempt_at = :nextAttemptAt,
                    lease_owner = NULL,
                    lease_until = NULL,
                    last_error = NULL,
                    retry_count = CASE WHEN :resetRetry THEN 0 ELSE retry_count END
                WHERE event_id = :eventId
                """.formatted(t());

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("status", mode.name())
                .addValue("nextAttemptAt", mode == OutboxStatus.RETRY ? Timestamp.from(now) : null)
                .addValue("resetRetry", resetRetry)
                .addValue("eventId", eventId);

        return named.update(sql, p);
    }

    /** Portable MySQL+Postgres: select ids then update */
    public int requeueFailed(int limit, boolean resetRetry, Instant now) {
        String select = """
                SELECT id
                FROM %s
                WHERE status = 'FAILED'
                ORDER BY id
                LIMIT :limit
                """.formatted(t());

        List<Long> ids = named.queryForList(select, Map.of("limit", limit), Long.class);
        if (ids.isEmpty()) return 0;

        String update = """
                UPDATE %s
                SET status = 'RETRY',
                    next_attempt_at = :now,
                    lease_owner = NULL,
                    lease_until = NULL,
                    last_error = NULL,
                    retry_count = CASE WHEN :resetRetry THEN 0 ELSE retry_count END
                WHERE id IN (:ids)
                """.formatted(t());

        return named.update(update, Map.of(
                "ids", ids,
                "now", Timestamp.from(now),
                "resetRetry", resetRetry
        ));
    }

    public int markFailedByEventId(String eventId, String error) {
        String sql = """
                UPDATE %s
                SET status = 'FAILED',
                    last_error = :error,
                    lease_owner = NULL,
                    lease_until = NULL
                WHERE event_id = :eventId
                """.formatted(t());

        return named.update(sql, Map.of("eventId", eventId, "error", safeErr(error)));
    }

    public int deleteByEventId(String eventId) {
        String sql = "DELETE FROM %s WHERE event_id = ?".formatted(t());
        return jdbc.update(sql, eventId);
    }

    private static Instant tsToInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String safeErr(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.length() > 2000 ? v.substring(0, 2000) : v;
    }
}
