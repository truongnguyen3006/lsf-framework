# lsf-example

> Ứng dụng demo nhỏ để minh họa cách nhiều module LSF phối hợp trong một Spring Boot service.

`lsf-example` không phải ecommerce consumer hoàn chỉnh. Module này dùng để chạy nhanh các capability cốt lõi của framework như event envelope, handler dispatch, retry/DLQ, quota/reservation, flash-sale reservation và outbox append.

## Demo được những gì?

| Capability | Module liên quan | Ý nghĩa |
|---|---|---|
| Publish/consume envelope event | `lsf-kafka-starter`, `lsf-eventing-starter` | Gửi payload và xử lý bằng `@LsfEventHandler` |
| Retry/DLQ | `lsf-kafka-starter` | Đẩy event lỗi sang DLQ |
| Idempotent dispatch | `lsf-eventing-starter` | Tránh xử lý trùng event |
| Quota/reservation | `lsf-quota-starter` | Reserve/confirm/release tài nguyên |
| Flash-sale demo | `lsf-quota-starter` | Minh họa anti-oversell |
| Outbox append | `lsf-outbox-core`, runtime outbox | Append envelope vào outbox table |

## Yêu cầu

- JDK 21
- Maven
- Docker nếu chạy Kafka, Redis, MySQL, Schema Registry

## Chạy nhanh

Từ root repo:

```bash
git clone https://github.com/truongnguyen3006/lsf-framework.git
cd lsf-framework
mvn -pl lsf-example -am spring-boot:run
```

Nếu cần hạ tầng local:

```bash
docker compose up -d kafka schema-registry mysql redis zipkin
```

Chạy với profile Docker:

```bash
mvn -pl lsf-example -am spring-boot:run -Dspring-boot.run.profiles=docker
```

Chạy với profile outbox MySQL:

```bash
mvn -pl lsf-example -am spring-boot:run -Dspring-boot.run.profiles=outbox-mysql
```

## Endpoint demo

| Method | Path | Mục đích |
|---|---|---|
| `POST` | `/send` | Publish event demo |
| `POST` | `/send-dup` | Gửi duplicate để kiểm tra idempotency |
| `POST` | `/send-fail` | Gửi event làm handler fail để kiểm tra retry/DLQ |
| `POST` | `/send-unknown` | Gửi event type chưa có handler |
| `POST` | `/quota/reserve` | Reserve quota bằng query/body đơn giản |
| `POST` | `/quota/confirm` | Confirm reservation |
| `POST` | `/quota/release` | Release reservation |
| `POST` | `/demo/flash-sale/orders/reserve` | Reserve một order flash sale |
| `POST` | `/demo/flash-sale/orders/{orderId}/confirm` | Confirm order flash sale |
| `POST` | `/demo/flash-sale/orders/{orderId}/release` | Release order flash sale |
| `GET` | `/demo/flash-sale/orders/{orderId}` | Xem trạng thái order demo |
| `POST` | `/outbox/append` | Append envelope vào outbox nếu bật outbox |

## Cấu hình đáng chú ý

Các file cấu hình chính:

```text
src/main/resources/
├─ application.yml
├─ application-docker.yml
└─ application-outbox-mysql.yml
```

Ví dụ các namespace thường gặp:

```yaml
lsf:
  kafka:
    bootstrap-servers: localhost:9092
    schema-registry-url: http://localhost:8081
  eventing:
    consume-topics:
      - demo-envelope-topic
  quota:
    enabled: true
    store: REDIS
  outbox:
    enabled: true
```

## Cấu trúc module

```text
lsf-example/
├─ src/main/java/com/demo/app/
│  ├─ DemoAppApplication.java
│  ├─ DemoHandlers.java
│  ├─ TestController.java
│  ├─ DemoQuotaController.java
│  ├─ OutboxSampleController.java
│  └─ flashsale/
└─ src/main/resources/
```

## Lưu ý

- Đây là demo framework, không phải service production.
- Nếu Kafka/Redis/MySQL chưa chạy, một số endpoint hoặc profile sẽ lỗi.
- Với consumer thực tế hơn, xem thêm [lsf-ecommerce-backend](https://github.com/truongnguyen3006/lsf-ecommerce-backend.git).
