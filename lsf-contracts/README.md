# lsf-contracts

> Module chứa các contract dùng chung cho toàn bộ LSF và các consumer service.

`lsf-contracts` là lớp nền giúp các module khác nói cùng một ngôn ngữ: event envelope, trace/request context, header convention, retry classification, sync HTTP error và quota commands.

## Dùng khi nào?

- Nhiều service cần thống nhất shape của event.
- Service muốn dùng `EventEnvelope` để gói metadata và payload.
- Module khác cần dùng chung `LsfErrorResponse`, `LsfRequestContext`, `CoreHeaders` hoặc quota command.
- Consumer muốn tránh copy DTO hạ tầng giữa nhiều service.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-contracts</artifactId>
</dependency>
```

## Thành phần chính

| Package | Nội dung |
|---|---|
| `core.envelope` | `EventEnvelope`, `EnvelopeBuilder`, `ErrorInfo` |
| `core.context` | `LsfRequestContext`, `LsfTraceContext` và holder tương ứng |
| `core.conventions` | `CoreHeaders`, `EventTypeFormat` |
| `core.exception` | `LsfRetryableException`, `LsfNonRetryableException`, retry decisions |
| `core.http` | `LsfErrorResponse` cho REST APIs |
| `quota` | `ReserveQuotaCommand`, `ConfirmReservationCommand`, `ReleaseReservationCommand`, `QuotaReserveResult` |

## EventEnvelope

`EventEnvelope` giúp mọi event có metadata thống nhất:

```java
EventEnvelope envelope = EnvelopeBuilder.wrap(
    objectMapper,
    "order.status.changed.v1",
    1,
    orderNumber,
    correlationId,
    causationId,
    "order-service",
    payload
);
```

Các metadata quan trọng gồm:

- `eventId`
- `eventType`
- `version`
- `aggregateId`
- `correlationId`
- `causationId`
- `occurredAtMs`
- `producer`
- `payload`

## Lưu ý thiết kế

- Module này nên giữ mỏng, ổn định và không phụ thuộc runtime nặng.
- Không đặt business DTO riêng của một service vào đây nếu DTO đó không phải contract dùng chung.
- Khi thay đổi `EventEnvelope` hoặc shared commands, cần cân nhắc backward compatibility cho consumer.
