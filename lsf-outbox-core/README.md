# lsf-outbox-core

> Module core cho outbox pattern, tách API chung khỏi runtime MySQL/PostgreSQL.

Outbox pattern giúp tránh lỗi dual-write: database đã commit nhưng publish Kafka thất bại, hoặc publish thành công nhưng database rollback. `lsf-outbox-core` cung cấp API chung để service append event vào outbox trong cùng transaction business.

## Dùng khi nào?

- Service cần publish event sau khi cập nhật database.
- Muốn code business không phụ thuộc trực tiếp vào MySQL/PostgreSQL runtime.
- Muốn giữ API `OutboxWriter` ổn định khi đổi runtime database.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-core</artifactId>
</dependency>
```

Thông thường service sẽ dùng runtime starter:

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-mysql-starter</artifactId>
</dependency>
```

## API chính

```java
public interface OutboxWriter {
    long append(EventEnvelope envelope, String topic, String key);
}
```

Ví dụ:

```java
@Transactional
public void updateOrder(OrderStatusChanged payload) {
    // update database

    outboxWriter.append(envelope, "order-status-envelope-topic", payload.orderNumber());
}
```

## Thành phần chính

| Class | Vai trò |
|---|---|
| `OutboxWriter` | Contract để append event vào outbox |
| `OutboxSql` | Helper SQL chung cho runtime/admin modules |

## Lưu ý

- Module core không tự chạy publisher.
- Cần chọn runtime DB tương ứng: `lsf-outbox-mysql-starter` hoặc `lsf-outbox-postgres-starter`.
- Consumer nên quản lý Flyway migration trong project của mình nếu cần kiểm soát version schema nghiêm ngặt.
