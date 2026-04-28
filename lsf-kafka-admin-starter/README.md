# lsf-kafka-admin-starter

> Starter cung cấp REST API nội bộ để inspect và replay Kafka DLQ records.

Module này phục vụ vận hành và demo evidence: xem topic DLQ, đọc record lỗi, kiểm tra headers gốc và replay record sang target topic.

## Dùng khi nào?

- Service dùng `lsf-kafka-starter` với DLQ.
- Cần xem nhanh record lỗi mà không mở Kafka CLI.
- Cần replay single record sau khi sửa bug handler.
- Cần surface cho frontend/admin evidence.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-kafka-admin-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

```yaml
lsf:
  kafka:
    admin:
      enabled: true
      base-path: /admin/kafka
      default-limit: 50
      max-limit: 200
      allow-replay: true
      dlq-suffix: .DLQ
      poll-timeout: 2s
```

## Endpoints

| Method | Path | Mục đích |
|---|---|---|
| `GET` | `/admin/kafka/dlq/topics` | Liệt kê DLQ topics |
| `GET` | `/admin/kafka/dlq/records` | Đọc records theo topic/partition/limit |
| `GET` | `/admin/kafka/dlq/records/{topic}/{partition}/{offset}` | Xem một record cụ thể |
| `POST` | `/admin/kafka/dlq/replay` | Replay record sang topic đích |

Payload replay:

```json
{
  "topic": "order-status-envelope-topic.DLQ",
  "partition": 0,
  "offset": 12,
  "targetTopic": "order-status-envelope-topic",
  "retainDlqHeaders": true
}
```

## Lưu ý bảo mật

- Đây là admin tool, không phải public API.
- Replay có thể tạo duplicate event; handler downstream cần idempotent.
- Nên bật `allow-replay=false` ở môi trường chỉ cho phép inspect.
