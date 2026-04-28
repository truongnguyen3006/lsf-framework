# lsf-eventing-starter

> Starter xử lý `EventEnvelope` theo mô hình handler với `@LsfEventHandler`, publisher API và idempotency tùy chọn.

Thay vì tự viết `@KafkaListener` rồi `switch` theo `eventType`, service có thể khai báo method xử lý business event. Starter sẽ scan handler, convert payload, dispatch và kiểm soát duplicate nếu bật idempotency.

## Dùng khi nào?

- Service consume nhiều loại event theo envelope.
- Muốn tách Kafka listener khỏi business handler.
- Muốn idempotency memory/Redis cho event processing.
- Muốn publish event theo `EventEnvelope` bằng API thống nhất.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-eventing-starter</artifactId>
</dependency>
```

Nên dùng kèm:

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-kafka-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

```yaml
lsf:
  eventing:
    producer-name: notification-service
    listener:
      enabled: true
    consume-topics:
      - order-status-envelope-topic
      - payment-processed-envelope-topic
    ignore-unknown-event-type: true
    idempotency:
      enabled: true
      store: memory
      ttl: 24h
      processing-ttl: 5m
      key-prefix: lsf:notification:idemp:{groupId}:
```

Với Redis idempotency:

```yaml
lsf:
  eventing:
    idempotency:
      enabled: true
      store: redis
      redis:
        enabled: true
        key-prefix: lsf:idemp:
```

## Viết handler

```java
@Component
public class OrderStatusHandlers {

    @LsfEventHandler(value = "order.status.changed.v1", payload = OrderStatusEvent.class)
    public void onOrderStatus(EventEnvelope envelope, OrderStatusEvent payload) {
        // business logic
    }
}
```

## Publish event

```java
publisher.publish(
    "order-status-envelope-topic",
    orderNumber,
    "order.status.changed.v1",
    orderNumber,
    payload
);
```

## Thành phần chính

| Class | Vai trò |
|---|---|
| `LsfEnvelopeListener` | Kafka listener đọc envelope topic |
| `HandlerRegistry` | Registry các method có `@LsfEventHandler` |
| `DefaultLsfDispatcher` | Dispatch envelope tới handler phù hợp |
| `IdempotentLsfDispatcher` | Bọc idempotency quanh dispatcher |
| `PayloadConverter` | Convert payload JSON sang type khai báo |
| `DefaultLsfPublisher` | Publish payload/envelope ra Kafka |

## Lưu ý

- `ignore-unknown-event-type=true` hữu ích khi một topic có event chưa được service quan tâm.
- Nếu handler có side effect, nên bật idempotency và thiết kế handler idempotent.
- Redis idempotency phù hợp multi-instance; memory idempotency chỉ phù hợp single-instance/dev/test.
