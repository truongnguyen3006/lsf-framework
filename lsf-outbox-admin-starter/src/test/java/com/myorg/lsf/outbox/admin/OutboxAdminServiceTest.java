package com.myorg.lsf.outbox.admin;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxAdminServiceTest {

    @Test
    void shouldClampLimitNormalizeFiltersAndDefaultOffset() {
        RecordingRepository repo = new RecordingRepository();
        LsfOutboxAdminProperties props = new LsfOutboxAdminProperties();
        props.setDefaultLimit(25);
        props.setMaxLimit(100);

        OutboxAdminService service = new OutboxAdminService(
                repo,
                props,
                Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC)
        );

        service.list(
                List.of(OutboxStatus.FAILED),
                " orders-topic ",
                "  order-1  ",
                "  payment.failed.v1  ",
                "  workflow-1  ",
                null,
                null,
                999,
                -5
        );

        assertThat(repo.listStatuses).containsExactly(OutboxStatus.FAILED);
        assertThat(repo.listTopic).isEqualTo(" orders-topic ");
        assertThat(repo.listMsgKey).isEqualTo("order-1");
        assertThat(repo.listEventType).isEqualTo("payment.failed.v1");
        assertThat(repo.listCorrelationId).isEqualTo("workflow-1");
        assertThat(repo.listLimit).isEqualTo(100);
        assertThat(repo.listOffset).isEqualTo(0);
    }

    @Test
    void shouldGuardRetryAndDeleteOperationsWhenDisabled() {
        RecordingRepository repo = new RecordingRepository();
        LsfOutboxAdminProperties props = new LsfOutboxAdminProperties();
        props.setAllowRetry(false);
        props.setAllowDelete(false);

        OutboxAdminService service = new OutboxAdminService(
                repo,
                props,
                Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.requeueByEventId("evt-1", OutboxStatus.RETRY, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Retry is disabled");

        assertThatThrownBy(() -> service.requeueFailed(10, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Retry is disabled");

        assertThatThrownBy(() -> service.deleteByEventId("evt-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Delete is disabled");
    }

    static class RecordingRepository extends JdbcOutboxAdminRepository {

        private List<OutboxStatus> listStatuses;
        private String listTopic;
        private String listMsgKey;
        private String listEventType;
        private String listCorrelationId;
        private int listLimit;
        private int listOffset;

        RecordingRepository() {
            super(null, null, "lsf_outbox");
        }

        @Override
        public List<OutboxAdminRow> list(List<OutboxStatus> statuses, String topic, String msgKey, String eventType,
                                         String correlationId,
                                         Instant from, Instant to, int limit, int offset) {
            this.listStatuses = statuses;
            this.listTopic = topic;
            this.listMsgKey = msgKey;
            this.listEventType = eventType;
            this.listCorrelationId = correlationId;
            this.listLimit = limit;
            this.listOffset = offset;
            return List.of();
        }
    }
}
