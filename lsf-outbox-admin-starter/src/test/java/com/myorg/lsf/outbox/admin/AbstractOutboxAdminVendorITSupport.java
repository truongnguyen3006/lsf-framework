package com.myorg.lsf.outbox.admin;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class AbstractOutboxAdminVendorITSupport {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-07T09:30:00Z");

    protected JdbcTemplate jdbc;
    protected JdbcOutboxAdminRepository repository;
    protected OutboxAdminService service;
    protected LsfOutboxAdminProperties properties;

    @BeforeEach
    void setUpVendorHarness() {
        DataSource dataSource = new DriverManagerDataSource(jdbcUrl(), username(), password());

        Flyway.configure()
                .dataSource(dataSource)
                .locations(flywayLocations())
                .load()
                .migrate();

        this.jdbc = new JdbcTemplate(dataSource);
        clearTable();

        this.repository = new JdbcOutboxAdminRepository(
                new NamedParameterJdbcTemplate(dataSource),
                jdbc,
                "lsf_outbox"
        );
        this.properties = new LsfOutboxAdminProperties();
        properties.setAllowRetry(true);
        properties.setAllowDelete(false);
        properties.setDefaultLimit(50);
        properties.setMaxLimit(200);
        this.service = new OutboxAdminService(
                repository,
                properties,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldListAndInspectRowsAgainstVendorSchema() {
        insertRow("orders", "key-1", "evt-1", "orders.created.v1", "SENT",
                Instant.parse("2026-04-06T10:00:00Z"), 0, null, null, null,
                Instant.parse("2026-04-06T10:01:00Z"));
        long failedId = insertRow("orders", "key-2", "evt-2", "orders.failed.v1", "FAILED",
                Instant.parse("2026-04-06T11:00:00Z"), 3, "boom", "node-a",
                Instant.parse("2026-04-06T11:05:00Z"), Instant.parse("2026-04-06T11:10:00Z"));
        insertRow("payments", "key-3", "evt-3", "payments.failed.v1", "FAILED",
                Instant.parse("2026-04-06T12:00:00Z"), 1, "boom", null, null, null);

        List<OutboxAdminRow> rows = service.list(
                List.of(OutboxStatus.FAILED),
                "orders",
                " key-2 ",
                " orders.failed.v1 ",
                " corr-evt-2 ",
                Instant.parse("2026-04-06T10:30:00Z"),
                Instant.parse("2026-04-06T11:30:00Z"),
                10,
                0
        );

        assertThat(rows).hasSize(1);
        OutboxAdminRow row = rows.getFirst();
        assertThat(row.id()).isEqualTo(failedId);
        assertThat(row.eventId()).isEqualTo("evt-2");
        assertThat(row.correlationId()).isEqualTo("corr-evt-2");
        assertThat(row.aggregateId()).isEqualTo("agg-evt-2");
        assertThat(row.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.retryCount()).isEqualTo(3);
        assertThat(row.leaseOwner()).isEqualTo("node-a");

        assertThat(service.findById(failedId))
                .get()
                .extracting(OutboxAdminRow::eventId, OutboxAdminRow::status)
                .containsExactly("evt-2", OutboxStatus.FAILED);
        assertThat(service.findByEventId("evt-2"))
                .get()
                .extracting(OutboxAdminRow::id, OutboxAdminRow::eventType)
                .containsExactly(failedId, "orders.failed.v1");
    }

    @Test
    void shouldRequeueSingleRowsAndBulkFailedRowsAgainstVendorSchema() {
        insertRow("orders", "key-1", "evt-1", "orders.failed.v1", "FAILED",
                Instant.parse("2026-04-06T10:00:00Z"), 5, "old", "node-a",
                Instant.parse("2026-04-06T10:05:00Z"), Instant.parse("2026-04-06T10:06:00Z"));
        insertRow("orders", "key-2", "evt-2", "orders.failed.v1", "FAILED",
                Instant.parse("2026-04-06T10:10:00Z"), 2, "old", "node-b",
                Instant.parse("2026-04-06T10:15:00Z"), Instant.parse("2026-04-06T10:16:00Z"));
        insertRow("orders", "key-3", "evt-3", "orders.failed.v1", "FAILED",
                Instant.parse("2026-04-06T10:20:00Z"), 4, "old", "node-c",
                Instant.parse("2026-04-06T10:25:00Z"), Instant.parse("2026-04-06T10:26:00Z"));

        int singleUpdated = service.requeueByEventId("evt-1", OutboxStatus.RETRY, true);
        assertThat(singleUpdated).isEqualTo(1);

        OutboxAdminRow singleRow = service.findByEventId("evt-1").orElseThrow();
        assertThat(singleRow.status()).isEqualTo(OutboxStatus.RETRY);
        assertThat(singleRow.retryCount()).isZero();
        assertThat(singleRow.lastError()).isNull();
        assertThat(singleRow.leaseOwner()).isNull();
        assertThat(singleRow.leaseUntil()).isNull();
        assertThat(singleRow.nextAttemptAt()).isEqualTo(FIXED_NOW);

        int bulkUpdated = service.requeueFailed(1, false);
        assertThat(bulkUpdated).isEqualTo(1);
        assertThat(service.findByEventId("evt-2").orElseThrow().status()).isEqualTo(OutboxStatus.RETRY);
        assertThat(service.findByEventId("evt-2").orElseThrow().retryCount()).isEqualTo(2);
        assertThat(service.findByEventId("evt-3").orElseThrow().status()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void shouldMarkFailedAndRespectDeleteGuardrailsAgainstVendorSchema() {
        insertRow("orders", "key-9", "evt-9", "orders.failed.v1", "RETRY",
                Instant.parse("2026-04-06T13:00:00Z"), 1, null, "node-z",
                Instant.parse("2026-04-06T13:05:00Z"), Instant.parse("2026-04-06T13:06:00Z"));

        int marked = service.markFailedByEventId("evt-9", "x".repeat(3000));
        assertThat(marked).isEqualTo(1);

        OutboxAdminRow failedRow = service.findByEventId("evt-9").orElseThrow();
        assertThat(failedRow.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failedRow.lastError()).hasSize(2000);
        assertThat(failedRow.leaseOwner()).isNull();
        assertThat(failedRow.leaseUntil()).isNull();

        assertThatThrownBy(() -> service.deleteByEventId("evt-9"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Delete is disabled");

        properties.setAllowDelete(true);
        int deleted = service.deleteByEventId("evt-9");
        assertThat(deleted).isEqualTo(1);
        assertThat(service.findByEventId("evt-9")).isEmpty();
    }

    protected long insertRow(String topic,
                             String msgKey,
                             String eventId,
                             String eventType,
                             String status,
                             Instant createdAt,
                             int retryCount,
                             String lastError,
                             String leaseOwner,
                             Instant leaseUntil,
                             Instant nextAttemptAt) {
        jdbc.update("""
                        INSERT INTO lsf_outbox (
                            topic, msg_key, event_id, event_type, correlation_id, aggregate_id,
                            envelope_json, status, created_at, sent_at, retry_count, last_error,
                            lease_owner, lease_until, next_attempt_at
                        ) VALUES (?, ?, ?, ?, ?, ?, %s, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(envelopePlaceholder()),
                topic,
                msgKey,
                eventId,
                eventType,
                "corr-" + eventId,
                "agg-" + eventId,
                "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\"}",
                status,
                Timestamp.from(createdAt),
                null,
                retryCount,
                lastError,
                leaseOwner,
                leaseUntil == null ? null : Timestamp.from(leaseUntil),
                nextAttemptAt == null ? null : Timestamp.from(nextAttemptAt)
        );
        return jdbc.queryForObject("SELECT id FROM lsf_outbox WHERE event_id = ?", Long.class, eventId);
    }

    protected abstract String jdbcUrl();

    protected abstract String username();

    protected abstract String password();

    protected abstract String[] flywayLocations();

    protected abstract String envelopePlaceholder();

    protected abstract void clearTable();
}
