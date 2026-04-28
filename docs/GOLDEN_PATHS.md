# Golden Paths

Tài liệu này định nghĩa các đường áp dụng được khuyến nghị cho adopter và cho Codex rollout các phase sau. Golden path không có nghĩa “mọi service đều phải giống nhau”; nó chỉ nghĩa là đây là các tổ hợp module LSF có framing rõ và ít rủi ro hơn phần còn lại.

## 1. Golden Path A: Event-Driven Service Cơ Bản

### Dùng khi nào

- service publish/consume Kafka;
- cần contract event và metadata chung;
- chưa cần durable publish sau DB transaction.

### Module set

- `lsf-contracts`
- `lsf-kafka-starter`
- `lsf-eventing-starter`
- `lsf-observability-starter` nếu cần metrics/MDC

### Đây là path khuyến nghị vì

- bám vào stable core hiện tại;
- đã có bằng chứng trong consumer ecommerce;
- public surface rõ và gọn.

### Migration từ custom code

- thay custom envelope bằng `EventEnvelope`;
- giữ topic cũ nếu cần, chỉ đổi wiring và handler contract;
- chuyển dần từ raw `@KafkaListener` sang `@LsfEventHandler`.

## 2. Golden Path B: MySQL Outbox Service

### Dùng khi nào

- service có transaction DB;
- phải publish integration event sau commit;
- đang chạy MySQL.

### Module set

- `lsf-contracts`
- `lsf-kafka-starter`
- `lsf-outbox-core`
- `lsf-outbox-mysql-starter`
- `lsf-observability-starter`
- `lsf-outbox-admin-starter` chỉ nếu cần internal operations

### Guidance

- đây là durable publish path được khuyến nghị nhất hiện tại;
- `lsf-outbox-admin-starter` là internal-only add-on, không phải default path phải mở.

### Migration từ custom code

- thay publish trực tiếp trong transaction bằng `OutboxWriter.append(...)`;
- bật poller sau khi schema/table đã sẵn sàng;
- giữ admin endpoints tắt nếu chưa có nhu cầu vận hành.

## 3. Golden Path C: Quota / Reservation Service

### Dùng khi nào

- service có concern reserve / confirm / release;
- muốn giảm oversell / overbooking logic riêng lẻ.

### Module set

- `lsf-contracts`
- `lsf-kafka-starter`
- `lsf-observability-starter`
- `lsf-quota-starter`

### Guidance

- đây là golden path phù hợp với inventory-style services;
- bắt đầu bằng policy source đơn giản (`STATIC` hoặc `JDBC`) rồi mới mở rộng cache/store.

### Migration từ custom code

- map resource key và request model sang quota contracts;
- thay reserve/release flow thủ công bằng `QuotaReservationFacade`;
- giữ business rules domain-specific ở service, không nhét vào framework layer.

## 4. Golden Path D: Internal Admin Tooling

### Dùng khi nào

- service cần inspect/requeue outbox rows;
- service cần inspect/replay DLQ records;
- operations path là internal-only.

### Module set

- `lsf-outbox-admin-starter`
- `lsf-kafka-admin-starter`

### Guidance

- chỉ bật khi có auth/network boundary rõ;
- không coi đây là public API cho external clients;
- không thêm mặc định vào mọi service nếu chưa có nhu cầu vận hành cụ thể.

## 5. Golden Path E: Sync HTTP Baseline Có Kiểm Soát

### Dùng khi nào

- service có internal REST ingress/egress;
- team muốn chuẩn hóa correlation headers, error response, discovery/resilience/auth;
- team chấp nhận rằng đây vẫn là `partial support`.

### Module set

- `lsf-service-web-starter`
- `lsf-http-client-starter`
- `lsf-discovery-starter`
- `lsf-resilience-starter`
- `lsf-security-starter`

### Guidance

- rollout path này theo từng service cụ thể, không ép toàn bộ repo cùng lúc;
- bắt đầu từ ingress contract trước, rồi mới migrate client path;
- cần focused validation riêng trên topology downstream thật.

## 6. Golden Path F: Bootstrap Service Mới

### Dùng khi nào

- team hoặc Codex cần tạo service mới trên nền LSF.

### Asset dùng

- `lsf-service-template`
- `lsf-example`
- `ops/`
- `ops/deployment/`

### Guidance

- `lsf-service-template` là starting point khuyến nghị cho structure;
- `lsf-example` dùng để xem composition và local validation;
- không deploy nguyên trạng các asset scaffold như production default.

## 7. Những path chưa nên coi là golden path mặc định

- `lsf-gateway-starter` cho org-wide gateway default
- `lsf-saga-starter` cho workflow phức tạp
- `lsf-outbox-postgres-starter` nếu chưa có checkpoint riêng
- full sync stack cho toàn bộ consumer repo cùng lúc

## 8. Rollout Order Cho Codex Ở Các Phase Sau

Nếu Codex được giao rollout tiếp trên consumer repos, thứ tự nên ưu tiên là:

1. chuẩn hóa `lsf.version` và BOM usage;
2. golden path A cho event-driven services;
3. golden path B cho MySQL services cần reliable publish;
4. golden path C cho inventory/quota style services;
5. golden path D cho internal operations nếu thật sự cần;
6. golden path E chỉ sau khi owner duyệt scope;
7. tránh mở rộng thẳng vào gateway/saga/scaffold assets như production defaults.

## 9. Owner Decisions Needed

- Chốt golden paths nào được xem là “blessed” cho `ecommerce-backend`.
- Chốt liệu sync HTTP path có được đưa lên tier ưu tiên ngang event-driven path hay chưa.
- Chốt khi nào PostgreSQL outbox path đủ điều kiện được nâng từ opt-in path lên golden path chính thức.
