# Upgrading

Tài liệu này tập trung vào hai kiểu nâng cấp:

- từ custom code sang LSF modules;
- từ một snapshot/milestone LSF sang snapshot/milestone mới hơn.

## 1. Nguyên tắc nâng cấp an toàn

- Align Java baseline về 21 trước khi thay concern-level code.
- Adopt theo concern, không adopt theo cảm hứng “thêm nhiều starter một lúc”.
- Ưu tiên `stable` modules trước, rồi mới đến `partial support`.
- Giữ rollback path đơn giản: mỗi bước migration nên chạy song song được với test/service behavior cũ.

## 2. Bước 0: Chuẩn hóa dependency management

Trước khi migrate concern cụ thể, adopter nên:

1. Đặt `lsf.version` tập trung ở root POM.
2. Import BOM từ `lsf-parent`.
3. Giữ Spring Boot / Spring Cloud theo baseline hiện tại của framework.
4. Xác nhận build dùng Java 21.

Nếu consumer chưa muốn chuyển sang BOM import ngay, tối thiểu vẫn nên gom `lsf.version` một chỗ duy nhất.

## 3. Migration Path Theo Concern

### 3.1 Từ custom event envelope / headers sang `lsf-contracts`

Áp dụng khi service đang:

- có DTO event riêng nhưng không có envelope chuẩn;
- tự truyền `correlationId`, `requestId`, `traceparent` theo nhiều format khác nhau;
- có sync error response không nhất quán.

Path khuyến nghị:

1. Chuẩn hóa event payload bọc trong `EventEnvelope`.
2. Đổi các exception retry semantics sang `LsfRetryAware`, `LsfRetryableException`, `LsfNonRetryableException`.
3. Chuẩn hóa sync error response quanh `LsfErrorResponse`.

### 3.2 Từ raw Kafka wiring sang `lsf-kafka-starter`

Áp dụng khi service đang:

- tự cấu hình `KafkaTemplate`, `ConsumerFactory`, retry và DLQ logic;
- có header conventions không đồng nhất giữa services.

Path khuyến nghị:

1. Giữ topic contract cũ, chỉ thay Kafka wiring bằng `lsf-kafka-starter`.
2. Ánh xạ retry / DLQ behavior hiện có sang property namespace `lsf.kafka.*`.
3. Chạy regression cho producer/consumer behavior trước khi đổi handler model.

### 3.3 Từ custom `@KafkaListener` business handlers sang `lsf-eventing-starter`

Áp dụng khi service đang:

- có nhiều `@KafkaListener` tự parse envelope;
- duplicate logic dispatch/event type mapping;
- cần idempotency hoặc publisher API thống nhất.

Path khuyến nghị:

1. Giữ `lsf-kafka-starter` làm transport baseline.
2. Chuyển business handler sang `@LsfEventHandler`.
3. Dùng `LsfPublisher` cho publish path mới thay vì gọi trực tiếp `KafkaTemplate`.
4. Chỉ bật idempotency sau khi đã xác nhận event type mapping ổn định.

### 3.4 Từ custom metrics/MDC quanh eventing sang `lsf-observability-starter`

Áp dụng khi service đã dùng eventing path và hiện:

- tự log correlation/MDC thủ công;
- tự đo metric xử lý event ở từng service.

Path khuyến nghị:

1. Giữ business handlers như cũ.
2. Thêm `lsf-observability-starter`.
3. So sánh metric names / tags và log expectations trước khi bỏ instrumentation custom cũ.

### 3.5 Từ custom transactional publish sang `lsf-outbox-core` + outbox runtime

Áp dụng khi service đang:

- update DB và publish event trong cùng transaction boundary một cách không bền vững;
- có event table/poller tự dựng và muốn chuyển sang framework abstraction.

Path khuyến nghị:

1. Thay business layer gọi Kafka trực tiếp trong transaction bằng `OutboxWriter.append(...)`.
2. Chọn runtime theo database:
   - MySQL: `lsf-outbox-mysql-starter`
   - PostgreSQL: `lsf-outbox-postgres-starter` sau khi có validation riêng
3. Chỉ bật publisher poller sau khi schema/table path đã xác nhận.
4. Nếu cần tooling nội bộ, thêm `lsf-outbox-admin-starter` nhưng không mở public mặc định.

### 3.6 Từ custom quota / reservation logic sang `lsf-quota-starter`

Áp dụng khi service đang:

- tự giữ state reserve/confirm/release;
- muốn giảm oversell/overbooking logic lặp lại.

Path khuyến nghị:

1. Map business resource key sang `QuotaKey`.
2. Chuyển flow reserve/confirm/release sang `QuotaReservationFacade`.
3. Cấu hình policy source trước: `STATIC` hoặc `JDBC`.
4. Chỉ thêm cache/Redis path khi đã có nhu cầu thật.

### 3.7 Từ custom sync HTTP filters/clients sang `lsf-service-web-starter` + `lsf-http-client-starter`

Áp dụng khi service đang:

- tự propagate correlation headers;
- có HTTP client wrappers riêng cho retry/auth/discovery;
- trả error response không nhất quán.

Path khuyến nghị:

1. Bắt đầu từ ingress với `lsf-service-web-starter`.
2. Sau đó migrate egress sang `@LsfHttpClient` + `@HttpExchange`.
3. Chỉ kéo thêm `lsf-discovery-starter`, `lsf-resilience-starter`, `lsf-security-starter` khi use case thật cần.
4. Treat toàn bộ path này là `partial support` cho đến khi service xác nhận chạy ổn trên topology thật.

### 3.8 Từ workflow custom sang `lsf-saga-starter`

Chỉ áp dụng khi workflow hiện tại:

- tương đối tuyến tính;
- có command/reply rõ;
- compensation có mô hình tuần tự;
- không cần branch/join phức tạp.

Nếu workflow vượt các giới hạn trên, chưa nên migrate sang `lsf-saga-starter` chỉ vì muốn “đồng bộ framework”.

## 4. Thứ tự rollout khuyến nghị

Đây là thứ tự ít rủi ro nhất cho phần lớn service:

1. `lsf-contracts`
2. `lsf-kafka-starter`
3. `lsf-eventing-starter`
4. `lsf-observability-starter`
5. `lsf-outbox-core` + runtime DB phù hợp
6. `lsf-quota-starter` nếu service có reservation concern
7. sync HTTP stack nếu thật sự cần
8. admin starters cho internal operations
9. `lsf-saga-starter` chỉ sau khi owner duyệt use case

## 5. Nâng cấp giữa các phiên bản LSF

Khi repo bắt đầu có milestone/release rõ hơn, path nâng cấp nên là:

1. Đọc `docs/RELEASE_POLICY.md`, `docs/COMPATIBILITY.md` và release notes tương ứng.
2. Đổi `lsf.version` một chỗ duy nhất.
3. Chạy build consumer với Java 21.
4. Re-run regression cho các concern đang thật sự dùng, không chỉ compile.
5. Review các property prefixes hoặc public APIs nằm trong diện `partial support`.

## Saga migration note from consumer evidence

- For `lsf-saga-starter`, the safest migration path is still a controlled rollout with a fallback mode in the consumer when the workflow is already live.
- If one saga step fans out to many downstream item-level requests, prefer a local adapter that uses the public reply fan-in helper and only emits one order-level reply back to the sequential saga.
- Do not force the starter to model branch/join orchestration just to remove a small amount of local glue.
- Keep `jdbc + direct` as the best-proven path unless your own service has separate evidence for `outbox` transport.

## 6. Owner Decisions Needed

- Chốt có cho phép bridge adapters trong giai đoạn migrate từ custom code sang LSF hay yêu cầu cutover trực tiếp.
- Chốt thứ tự rollout chuẩn cho `ecommerce-backend` nếu các phase sau tiếp tục mở rộng adoption.
- Chốt khi nào sync HTTP stack và saga path đủ điều kiện được đưa vào migration guidance “khuyến nghị mặc định”.
