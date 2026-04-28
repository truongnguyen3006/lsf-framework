# lsf-outbox-mysql-starter

> Runtime outbox cho MySQL: writer, repository, publisher poller, lease/retry và metrics.

Module này hiện là runtime outbox được kiểm chứng rõ nhất trong consumer ecommerce, đặc biệt ở `order-service` và `product-service`.

## Dùng khi nào?

- Service dùng MySQL và cần reliable event publishing.
- Muốn append event trong transaction rồi để background publisher gửi Kafka.
- Cần retry/backoff/lease để nhiều instance xử lý outbox an toàn hơn.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-mysql-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

```yaml
lsf:
  outbox:
    enabled: true
    table: lsf_outbox
    metrics:
      enabled: true
    publisher:
      enabled: true
      scheduling-enabled: true
      initial-delay: 1s
      poll-interval: 1s
      batch-size: 50
      lease: 10s
      backoff-base: 1s
      backoff-max: 60s
      max-retries: 10
      send-timeout: 10s
      claim-strategy: SKIP_LOCKED
```

## Flow hoạt động

```text
Business transaction
  -> update domain tables
  -> outboxWriter.append(envelope, topic, key)
  -> commit

Background publisher
  -> claim pending rows
  -> publish to Kafka
  -> mark SENT or RETRY/FAILED
```

## Thành phần chính

| Class | Vai trò |
|---|---|
| `JdbcOutboxWriter` | Append event vào bảng outbox |
| `JdbcOutboxRepository` | Query/claim/update rows |
| `OutboxPublisher` | Poll và publish event |
| `OutboxMetrics` | Metrics cho pending/sent/failed/retry |
| `OutboxPublisherHooks` | Hook tùy biến quanh publisher |

## Migration

Module có SQL schema trong classpath tại `META-INF/spring/lsf/sql/mysql`. Với consumer có Flyway riêng, nên copy hoặc include location này rõ ràng:

```properties
spring.flyway.locations=classpath:db/migration,classpath:META-INF/spring/lsf/sql/mysql
```

## Lưu ý

- `lsf.outbox.enabled=true` bật writer/repository; `lsf.outbox.publisher.enabled=true` mới bật publisher.
- Nếu nhiều instance cùng publish, nên ưu tiên `SKIP_LOCKED` khi database hỗ trợ.
- Không xóa outbox rows khi chưa có chiến lược retention/audit rõ ràng.
