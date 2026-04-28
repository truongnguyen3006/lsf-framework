# lsf-kafka-starter

> Starter chuẩn hóa Kafka producer/consumer defaults, retry và DLQ cho các service LSF.

Module này giúp service mới không phải tự bootstrap lại Kafka từ đầu. Các default được chọn theo hướng an toàn hơn cho hệ event-driven: producer idempotence, `acks=all`, retry có kiểm soát, consumer factory, listener container factory và DLQ recoverer.

## Dùng khi nào?

- Service publish hoặc consume Kafka messages.
- Muốn dùng chung Kafka defaults giữa nhiều service.
- Cần retry/DLQ baseline để lỗi không bị mất âm thầm.
- Muốn dùng serializer/deserializer theo convention LSF.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-kafka-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

```yaml
lsf:
  kafka:
    bootstrap-servers: localhost:9092
    schema-registry-url: http://localhost:8081
    producer:
      acks: all
      idempotence: true
      retries: 10
      max-in-flight: 5
      compression: snappy
      linger-ms: 5
      batch-size: 65536
    consumer:
      group-id: order-service
      auto-offset-reset: earliest
      batch: false
      concurrency: 2
      max-poll-records: 200
      retry:
        attempts: 3
        backoff: 200ms
      json-value-type: com.myorg.lsf.contracts.core.envelope.EventEnvelope
    dlq:
      enabled: true
      suffix: .DLQ
    observability:
      observation-enabled: true
```

## Bean/runtime được cung cấp

| Thành phần | Vai trò |
|---|---|
| `ProducerFactory` / `KafkaTemplate` | Producer baseline |
| `ConsumerFactory` | Consumer baseline |
| `ConcurrentKafkaListenerContainerFactory` | Listener factory theo config LSF |
| `DefaultErrorHandler` | Retry và recover sang DLQ |
| `DeadLetterPublishingRecoverer` | Publish message lỗi sang DLQ |
| `SerdeFactory` | Helper tạo Serde cho Kafka/Kafka Streams |
| `LsfDlqReasonClassifier` | Phân loại lý do vào DLQ |

## DLQ headers

DLQ record được bổ sung metadata để debug/replay dễ hơn, ví dụ:

- topic/partition/offset gốc
- exception class/message
- DLQ reason
- correlation id/event metadata nếu có

## Lưu ý

- `lsf.kafka.dlq.enabled=true` chỉ có ý nghĩa khi service dùng listener factory do starter tạo.
- Retry không tự làm operation trở nên idempotent; handler cần an toàn trước duplicate event.
- Nếu dùng Confluent Schema Registry, cần đảm bảo service có dependency serializer phù hợp.
