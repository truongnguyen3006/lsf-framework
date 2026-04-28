# lsf-outbox-postgres-starter

> Runtime outbox cho PostgreSQL, cùng contract với MySQL runtime nhưng dùng SQL/schema phù hợp PostgreSQL.

Module này phục vụ các service dùng PostgreSQL cần reliable event publishing sau transaction database.

## Dùng khi nào?

- Service dùng PostgreSQL.
- Cần outbox writer và background publisher.
- Muốn giữ API `OutboxWriter` giống MySQL để dễ chuyển runtime.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-postgres-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

```yaml
lsf:
  outbox:
    enabled: true
    table: lsf_outbox
    publisher:
      enabled: true
      scheduling-enabled: true
      poll-interval: 1s
      batch-size: 50
      lease: 10s
      claim-strategy: SKIP_LOCKED
```

## Thành phần chính

| Class | Vai trò |
|---|---|
| `JdbcOutboxWriter` | Append envelope vào `lsf_outbox` |
| `JdbcOutboxRepository` | Claim/update rows theo PostgreSQL SQL |
| `OutboxPublisher` | Publish pending rows ra Kafka |
| `OutboxMetrics` | Metrics outbox |

## Migration

Schema mặc định nằm ở:

```text
src/main/resources/db/migration/V1__create_lsf_outbox.sql
```

Nếu service dùng Flyway riêng, hãy đưa migration này vào lịch sử migration của consumer hoặc cấu hình Flyway location phù hợp.

## Lưu ý

- Consumer ecommerce hiện validate MySQL sâu hơn PostgreSQL.
- Không nên bật đồng thời MySQL và PostgreSQL outbox runtime trong cùng một service.
- Cần cấu hình datasource, transaction manager và Kafka producer trước khi bật publisher.
