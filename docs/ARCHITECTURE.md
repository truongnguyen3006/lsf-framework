# LSF Architecture

Tài liệu này tổng hợp cách repository hiện tại hỗ trợ đề tài:

> Framework supporting development of large-scale applications based on microservices architecture

Phase 8 tập trung vào việc đặt lại framing ở mức kiến trúc: mô tả rõ từng capability đã được thêm qua các phase, vì sao nó thuộc framework, service developer dùng nó như thế nào, và phần nào vẫn là future work.

## 1. Định vị kiến trúc của LSF

LSF không cố xây một business system hay một platform độc lập. Nó đóng vai trò một tầng framework nằm giữa:

- Spring Boot/Spring Cloud ecosystem ở phía dưới
- business microservices của adopter ở phía trên

Giá trị của repo nằm ở việc chuẩn hóa và tái sử dụng các concern cross-cutting vốn hay bị viết lặp ở nhiều service:

- shared contracts và metadata
- async messaging conventions
- sync HTTP conventions
- reliable event publishing
- orchestration và quota control
- baseline observability, admin và deployment

## 2. Diễn tiến capability qua các phase

### Phase 1. Platform foundations

**Đã thêm**

- `lsf-config-starter`
- `lsf-discovery-starter`
- `lsf-gateway-starter`
- `lsf-security-starter`
- `lsf-resilience-starter`

**Vì sao thuộc framework**

- Đây là các concern nền tảng, lặp lại ở nhiều service nhưng không nên bị mỗi team tự cài riêng.
- Chúng thiết lập convention chung cho cấu hình, service lookup, auth, error handling ở tầng gọi service và resilience policy.
- Chúng bám vào Spring ecosystem hiện có thay vì tái tạo config server, registry server hoặc gateway product riêng.

**Service developer dùng như thế nào**

- Bật `lsf-config-starter` khi cần chuẩn hóa `spring.config.import` theo `FILE`, `CONFIGTREE` hoặc `CONFIG_SERVER`.
- Bật `lsf-discovery-starter` để dùng `DiscoveryClient` sẵn có hoặc static registry local qua `LsfServiceLocator`.
- Bật `lsf-gateway-starter` cho internal/edge gateway đơn giản dựa trên Spring Cloud Gateway.
- Bật `lsf-security-starter` cho `API_KEY` hoặc `JWT` baseline.
- Bật `lsf-resilience-starter` để dùng `LsfResilienceExecutor` với retry/circuit/timeout/rate-limit theo từng instance.

**Future work còn lại**

- advanced load-balancing và health-aware discovery
- gateway hardening sâu hơn
- reactive security parity
- policy observability sâu hơn cho resilience events

### Phase 2. Saga / orchestration / workflow

**Đã thêm**

- `lsf-saga-starter`

**Vì sao thuộc framework**

- Orchestration theo event, timeout và compensation là pattern có thể tái sử dụng giữa nhiều hệ microservices lớn.
- Nếu để từng service tự dựng orchestration runtime, logic state machine và metadata propagation rất dễ phân mảnh.
- Starter này biến saga từ “ý tưởng kiến trúc” thành runtime framework-level có definition, state repository và timeout scanner.

**Service developer dùng như thế nào**

- Khai báo `SagaDefinition` và `SagaStep`.
- Dùng `LsfSagaOrchestrator` để khởi động workflow.
- Chọn `memory` hoặc `jdbc` làm store.
- Cho phép saga command/reply đi qua eventing hiện có; khi có outbox thì runtime có thể tận dụng `OutboxWriter`.

**Future work còn lại**

- branch/parallel join phức tạp hơn
- dashboard/admin cho lifecycle saga
- reactive orchestration model

### Phase 3. Synchronous service-to-service HTTP

**Đã thêm**

- `lsf-service-web-starter`
- `lsf-http-client-starter`

**Vì sao thuộc framework**

- Microservices lớn không chỉ giao tiếp bằng event; sync HTTP nội bộ vẫn là nhu cầu phổ biến.
- Framework cần chuẩn hóa request context, canonical headers, error response và retry classification để sync path không trở thành “thế giới riêng”.
- Hai starter này kết nối lại discovery, security và resilience đã có từ Phase 1.

**Service developer dùng như thế nào**

- `lsf-service-web-starter` để nhận/generate `correlation-id`, `causation-id`, `request-id` và trả `LsfErrorResponse` nhất quán.
- `lsf-http-client-starter` để khai báo `@LsfHttpClient` trên Spring `@HttpExchange`.
- Cấu hình downstream endpoints qua `lsf-discovery-starter`.
- Dùng `lsf-resilience-starter` cho retry/timeout/circuit breaker trên từng call path.

**Future work còn lại**

- reactive/WebFlux parity
- OAuth2 client-credentials flow hoàn chỉnh
- gRPC conventions/runtime

### Phase 4. Service scaffolding

**Đã thêm**

- `lsf-service-template`

**Vì sao thuộc framework**

- Một framework thesis-grade không nên chỉ có các starter rời rạc; nó cần chỉ ra cách adopter ghép các phần đó thành một service mới.
- Template giúp repo thể hiện “framework consumption path” rõ ràng hơn, thay vì để người đọc tự suy luận.

**Service developer dùng như thế nào**

- Copy/adapt module này để tạo service mới.
- Đổi `artifactId`, package root, service ids, event types và downstream clients.
- Chọn thêm outbox runtime theo database thật.

**Future work còn lại**

- template specialization theo nhiều archetype service hơn
- generator/scaffolder tự động nếu cần

### Phase 5. Operational maturity

**Đã thêm**

- `lsf-observability-starter`
- `lsf-kafka-admin-starter`
- `lsf-outbox-admin-starter`
- `ops/README.md`
- monitoring assets trong `ops/monitoring`

**Vì sao thuộc framework**

- Large-scale microservices không chỉ cần chạy đúng mà còn cần được quan sát, debug và vận hành có kiểm soát.
- Observability và admin endpoints ở đây là concern framework-level vì chúng bọc quanh runtime generic như dispatcher, DLQ và outbox.

**Service developer dùng như thế nào**

- Bật `lsf-observability-starter` để thêm MDC, metrics và observation quanh `LsfDispatcher`.
- Bật `lsf-kafka-admin-starter` khi cần inspect/replay DLQ records nội bộ.
- Bật `lsf-outbox-admin-starter` khi cần list/requeue/mark failed/delete rows của outbox.
- Dùng `ops/monitoring` như baseline dashboard/Prometheus/Alertmanager stack.

**Future work còn lại**

- richer dashboards và alerts theo domain
- deeper end-to-end tracing validation
- admin tooling sâu hơn cho saga và multi-runtime operations

### Phase 6. CI/CD and deployment baselines

**Đã thêm**

- `.github/workflows/ci.yml`
- `.github/workflows/release-candidate.yml`
- `docker-compose.yml`
- `lsf-example/Dockerfile`
- `lsf-service-template/Dockerfile`
- `ops/deployment/README.md`
- `ops/deployment/helm/lsf-service`

**Vì sao thuộc framework**

- Một framework luận văn không nhất thiết phải thành platform phát hành hoàn chỉnh, nhưng nên có evidence rằng nó có thể được build, package và chạy theo một baseline deployment hợp lý.
- Các artifact này tăng tính defensible của repo ở góc độ “adoption path” và “operational viability”.

**Service developer dùng như thế nào**

- Dùng workflow CI làm baseline verify/lint/build sanity.
- Dùng Dockerfile/Compose để chạy example và template trên local/container.
- Dùng Helm chart như skeleton khi triển khai service Spring Boot dùng LSF.

**Future work còn lại**

- registry publish thực sự
- GitOps manifests
- secrets/topic provisioning/cloud-specific charts

### Phase 7. Test maturity

**Đã thêm**

- focused regression suites và test bổ sung đáng kể cho:
  - `lsf-kafka-starter`
  - `lsf-eventing-starter`
  - `lsf-observability-starter`
  - `lsf-outbox-admin-starter`
  - `lsf-kafka-admin-starter`
- runtime integration test tối thiểu cho `lsf-saga-starter`, dùng `direct transport` + JDBC store + `EmbeddedKafka`
- broker-backed Testcontainers regression cho `lsf-kafka-admin-starter` để verify đọc DLQ records, replay single record, replay headers và replay metrics
- cross-module runtime test tối thiểu cho `lsf-kafka-starter` + `lsf-eventing-starter` + `lsf-observability-starter` để verify publish, dispatch, metadata propagation, metrics và observation
- cross-module runtime test tối thiểu cho `lsf-service-web-starter` + `lsf-http-client-starter` + `lsf-security-starter` + `lsf-resilience-starter` để verify API key auth, request/trace propagation, retryable retry và non-retryable error decode
- vendor-specific DB regression cho `lsf-outbox-admin-starter` trên MySQL/PostgreSQL, dùng lại schema từ runtime modules
- Maven profile `heavy-integration` để tách các suite container-backed khỏi lane verify mặc định

**Vì sao thuộc framework**

- Thesis-grade framework cần chứng minh hành vi runtime thực tế, không chỉ có code structure.
- Việc tăng test maturity giúp repo bảo vệ được các capability cross-cutting như retry/DLQ, idempotency, admin endpoints và metrics/tracing propagation.

**Service developer dùng như thế nào**

- Chạy focused suites để verify các concern runtime quan trọng.
- Dùng test hiện có làm reference khi mở rộng starter hoặc tích hợp capability tương tự vào service adopter.
- Dùng sync HTTP runtime test làm reference cho servlet-based internal REST path tối thiểu: auth qua API key, propagate `correlation-id`/`causation-id`/`request-id`, decode `LsfErrorResponse` và để `LsfResilienceExecutor` retry đúng theo `retryable`.
- Dùng saga integration test làm reference cho sequential orchestration tối thiểu: start saga, dispatch command, consume reply theo `correlationId`, complete hoặc compensate state transition.
- Dùng `mvn -B -ntp verify` cho lane mặc định và bật `-Pheavy-integration` khi cần chạy các suite broker/database thật.

**Future work còn lại**

- broker-backed end-to-end tests rộng hơn cho full listener failure -> retry -> DLQ -> replay chain
- discovery-specific / reactive matrix sâu hơn cho sync HTTP path, thay vì chỉ local runtime fixture hiện tại
- outbox transport integration cho `lsf-saga-starter` và matrix runtime sâu hơn giữa saga + eventing + outbox
- vendor-specific DB regression suites cho các lớp DB-sensitive khác ngoài outbox admin

### Phase 8. Documentation reconciliation and final positioning

**Đã thêm**

- README gốc được viết lại theo hướng framework-first
- module docs được đối chiếu lại với source code hiện tại
- bổ sung tài liệu kiến trúc này
- sửa các ví dụ cấu hình bị drift với property names thật

**Vì sao thuộc framework**

- Nếu tài liệu không khớp code, người đọc sẽ không phân biệt được capability thật với ý tưởng chưa hoàn thành.
- Một luận văn về framework cần một narrative kiến trúc trung thực, coherent và có thể phòng vệ được trước câu hỏi “service developer thực sự dùng repo này như thế nào”.

**Service developer dùng như thế nào**

- Bắt đầu ở `README.md`
- xem `docs/ARCHITECTURE.md` để hiểu bức tranh capability
- mở README theo module để lấy config/API đúng với source code hiện tại

**Future work còn lại**

- tiếp tục cập nhật docs khi capability runtime thay đổi
- thêm architecture decision records nếu repo tiếp tục phát triển sau luận văn

## 3. Integrated runtime view

```text
Shared contracts
  lsf-contracts
    -> EventEnvelope
    -> request/trace context
    -> retry-aware exceptions
    -> LsfErrorResponse

Synchronous path
  client
    -> lsf-gateway-starter (optional)
    -> lsf-service-web-starter
    -> application service
       -> lsf-http-client-starter
       -> lsf-discovery-starter
       -> lsf-security-starter
       -> lsf-resilience-starter

Asynchronous path
  application service
    -> lsf-eventing-starter / LsfPublisher
    -> lsf-kafka-starter
    -> Kafka
    -> lsf-eventing-starter / LsfDispatcher
    -> lsf-observability-starter
    -> business handlers

Reliable publishing
  application service + database transaction
    -> lsf-outbox-core / OutboxWriter
    -> lsf-outbox-mysql-starter or lsf-outbox-postgres-starter
    -> background publisher
    -> Kafka

Coordination
  lsf-quota-starter
  lsf-saga-starter

Operations
  lsf-kafka-admin-starter
  lsf-outbox-admin-starter
  ops/monitoring
  ops/deployment
```

Nhìn theo góc độ này, LSF hỗ trợ large-scale microservices bằng cách:

- giảm lặp lại ở tầng messaging và sync integration
- giữ metadata/correlation nhất quán giữa HTTP và Kafka
- tăng độ tin cậy khi publish event sau transaction
- cung cấp building blocks cho contention control và orchestration
- bổ sung visibility và baseline deployment đủ để repo không chỉ dừng ở mức source code

## 4. Tuyên bố trạng thái hỗ trợ một cách trung thực

LSF hiện có thể được bảo vệ như một framework hỗ trợ phát triển microservices quy mô lớn ở các điểm sau:

- Có cấu trúc multi-module rõ ràng, tách concern theo lớp framework thay vì theo business domain.
- Có capability thật ở cả sync và async path.
- Có abstractions dùng chung giữa nhiều module, thay vì chỉ là tập ví dụ rời rạc.
- Có test framework-level cho phần runtime quan trọng.
- Sync HTTP stack giờ đã có bằng chứng runtime tối thiểu cho servlet path qua `lsf-service-web-starter` + `lsf-http-client-starter` + `lsf-security-starter` + `lsf-resilience-starter`, dù vẫn chưa mở rộng sang reactive path hay discovery matrix rộng hơn.
- `lsf-saga-starter` giờ đã có bằng chứng runtime cho sequential orchestration với success path và compensation path tối thiểu, dù scope vẫn chưa mở rộng sang outbox transport hay graph phức tạp.
- Có scaffold, ops và deployment baselines để chứng minh đường áp dụng thực tế.

Nhưng LSF không nên bị diễn giải quá mức ở các điểm sau:

- không phải mọi module đều production-hardened như nhau
- chưa có full-system validation cho mọi flow xuyên nhiều starter
- chưa có đầy đủ operational surface như một internal platform lớn
- một số asset vẫn đúng nghĩa scaffold/skeleton hơn là sản phẩm hoàn tất
