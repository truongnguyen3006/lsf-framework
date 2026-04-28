# lsf-resilience-starter

> Starter gom các policy resilience nền tảng trên Resilience4j: retry, circuit breaker, timeout và rate limit.

Module này cung cấp `LsfResilienceExecutor` và `LsfResiliencePolicyResolver` để các module khác, đặc biệt là `lsf-http-client-starter`, có thể áp dụng policy nhất quán khi gọi downstream.

## Dùng khi nào?

- Service gọi downstream HTTP/gRPC/Kafka admin operation có thể lỗi tạm thời.
- Muốn cấu hình default policy và override theo từng integration.
- Muốn tránh copy retry/circuit breaker setup ở từng service.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-resilience-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

```yaml
lsf:
  resilience:
    enabled: true
    defaults:
      retry:
        enabled: true
        max-attempts: 3
        wait-duration: 200ms
      timeout:
        enabled: true
        duration: 2s
      circuit-breaker:
        enabled: true
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
    instances:
      inventory-client:
        retry:
          max-attempts: 2
        timeout:
          duration: 1s
```

## Thành phần chính

| Class | Vai trò |
|---|---|
| `LsfResilienceExecutor` | Bọc execution bằng policy đã resolve |
| `LsfResiliencePolicyResolver` | Tìm policy theo `resilienceId`, fallback về defaults |
| `LsfResilienceComponents` | Giữ các registry/component Resilience4j |
| `LsfResilienceProperties` | Binding namespace `lsf.resilience` |

## Ví dụ sử dụng

```java
String result = executor.execute("inventory-client", () -> inventoryGateway.call());
```

## Lưu ý

- Resilience không thay thế idempotency. Với operation có side effect, cần thiết kế retry an toàn.
- Timeout quá ngắn có thể tạo false failure khi hệ thống đang tải cao.
- Nên đặt policy riêng cho các downstream quan trọng thay vì dùng default cho mọi thứ.
