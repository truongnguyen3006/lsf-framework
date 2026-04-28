# lsf-saga-starter

> Starter hỗ trợ saga orchestration tuần tự theo event, có timeout, compensation, store in-memory/JDBC và transport direct/outbox.

Module này giúp service điều phối workflow nhiều bước mà không nhét toàn bộ logic vào một handler lớn. Trong consumer ecommerce, module được dùng để demo order checkout saga với inventory và payment.

## Dùng khi nào?

- Workflow có nhiều bước bất đồng bộ, ví dụ validate inventory -> process payment -> update order.
- Cần timeout và compensation khi một bước không trả lời.
- Cần lưu trạng thái saga để quan sát/recover.
- Cần fan-in cục bộ cho trường hợp một bước chờ nhiều reply nhỏ trước khi đi tiếp.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-saga-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

```yaml
lsf:
  saga:
    enabled: true
    store: jdbc
    transport:
      mode: direct
    observe-dispatch: true
    consume-matching-events: true
    default-step-timeout: 45s
    timeout-scanner:
      enabled: true
      poll-interval: 1s
      batch-size: 50
    jdbc:
      table: lsf_saga_instance
      initialize-schema: never
```

## Định nghĩa saga

```java
@Bean
SagaDefinition<OrderCheckoutState> orderCheckoutSaga() {
    return SagaDefinition.builder("order-checkout-saga", OrderCheckoutState.class)
        .step(SagaStep.<OrderCheckoutState>builder("validate-inventory")
            .command(ctx -> new SagaCommand(
                "inventory.validation.requested.v1",
                ctx.state().orderNumber(),
                ctx.state()
            ))
            .timeout(Duration.ofSeconds(30))
            .onReply("inventory.validation.succeeded.v1", InventorySucceeded.class,
                (ctx, envelope, payload) -> SagaReplyDecision.proceed(ctx.state()))
            .onReply("inventory.validation.failed.v1", InventoryFailed.class,
                (ctx, envelope, payload) -> SagaReplyDecision.fail(ctx.state()))
            .build())
        .build();
}
```

## Thành phần chính

| Class | Vai trò |
|---|---|
| `LsfSagaOrchestrator` | Start/advance saga instance |
| `SagaDefinition` / `SagaStep` | Mô tả workflow |
| `SagaInstanceRepository` | Lưu trạng thái saga |
| `JdbcSagaInstanceRepository` | Store JDBC |
| `SagaAwareLsfDispatcher` | Bắt reply events để advance saga |
| `SagaReplyFanInSession` | Helper gom nhiều reply con trước khi quyết định bước tiếp |
| `DefaultSagaEventPublisher` | Publish command/reply theo transport |

## Migration

Nếu dùng `store=jdbc`, consumer cần có bảng `lsf_saga_instance`. Trong ecommerce backend, migration nằm ở:

```text
order-service/src/main/resources/db/migration/V3__create_lsf_saga_instance.sql
```

## Trạng thái và giới hạn

- Runtime được kiểm chứng tốt nhất hiện tại là `jdbc + direct`.
- `outbox` transport có sẵn ở mức thiết kế/runtime nhưng chưa có bằng chứng sâu như direct mode.
- Module phù hợp cho flow tuần tự có compensation; chưa nên mô tả như workflow engine tổng quát cho mọi branching/parallel graph.
- Với production, cần bổ sung monitoring, retention, replay/recovery policy và test lỗi hạ tầng sâu hơn.
