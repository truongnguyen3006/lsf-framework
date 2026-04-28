# lsf-quota-starter

> Starter triển khai quota/reservation workflow cho tài nguyên hữu hạn: `reserve -> confirm -> release`.

Module này giải quyết các bài toán như flash sale, inventory hold, booking slot hoặc coupon cap, nơi nhiều request cùng tranh chấp một giới hạn tài nguyên.

## Dùng khi nào?

- Cần tránh oversell/overbooking.
- Cần giữ tài nguyên tạm thời trong lúc chờ thanh toán/xác nhận.
- Cần release reservation khi workflow thất bại hoặc timeout.
- Cần chạy multi-instance với Redis-backed quota state.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-quota-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

```yaml
lsf:
  quota:
    enabled: true
    store: REDIS
    key-prefix: lsf:quota:
    default-hold-seconds: 30
    keep-alive-seconds: 86400
    allow-release-confirmed: false
    metrics-enabled: true
    provider:
      mode: AUTO
      jdbc:
        table: quota_policy
        enabled-only: true
      cache:
        mode: MEMORY_REDIS
        ttl-seconds: 30
        local-max-size: 10000
        redis-prefix: lsf:quota:policy:
```

Static policy:

```yaml
lsf:
  quota:
    policies:
      - key: shopA:sku:NIK1-WHITE-38
        limit: 100
        hold-seconds: 120
```

## API chính

```java
QuotaResult result = quotaService.reserve(
    QuotaRequest.builder()
        .quotaKey("shopA:sku:NIK1-WHITE-38")
        .requestId("order-1001:NIK1-WHITE-38")
        .amount(2)
        .limit(100)
        .hold(Duration.ofSeconds(120))
        .build()
);

quotaService.confirm("shopA:sku:NIK1-WHITE-38", "order-1001:NIK1-WHITE-38");
quotaService.release("shopA:sku:NIK1-WHITE-38", "order-1001:NIK1-WHITE-38");
```

## Thành phần chính

| Class | Vai trò |
|---|---|
| `QuotaService` | API reserve/confirm/release |
| `QuotaReservationFacade` | Facade lấy policy rồi gọi backend |
| `MemoryQuotaService` | State in-memory cho dev/test |
| `RedisQuotaService` | State Redis cho multi-instance |
| `QuotaPolicyProvider` | Nguồn policy static/JDBC/cache |
| `QuotaMetrics` | Metrics cho reserve/confirm/release |

## Bảng policy JDBC mẫu

```sql
CREATE TABLE quota_policy (
  quota_key VARCHAR(255) PRIMARY KEY,
  quota_limit INT NOT NULL,
  hold_seconds INT NULL,
  enabled TINYINT NOT NULL DEFAULT 1
);
```

## Lưu ý

- `requestId` nên ổn định theo business action để duplicate reserve không làm tăng used quota.
- Redis store phù hợp hệ nhiều instance; memory store không chia sẻ state giữa instances.
- `allow-release-confirmed=false` giúp tránh rollback nhầm reservation đã confirm.
