# Platform Adoption Contract

## 1. LSF là gì và không phải là gì

LSF là một framework/platform layer đặt giữa:

- Spring Boot / Spring Cloud ecosystem ở bên dưới.
- business microservices của adopter ở bên trên.

LSF hiện không nên được diễn giải như:

- một internal developer platform hoàn chỉnh thay thế toàn bộ Kubernetes / Spring Cloud / gateway / security ecosystem;
- một workflow engine tổng quát;
- một production-ready ops suite đầy đủ cho mọi môi trường;
- một bộ starter mà service nào cũng phải kéo hết vào.

Framing đúng cho Phase 1 là: LSF cung cấp adoption contracts rõ ràng để service và Codex rollout các phase sau nhất quán hơn, trong khi vẫn giữ trung thực về maturity không đồng đều giữa các module.

## 2. Baseline Adoption Contract

Adopter và Codex nên coi các điểm sau là contract nền:

- Build baseline là Java 21.
- Spring Boot baseline hiện tại là `3.5.7`.
- Spring Cloud baseline hiện tại là `2025.0.0`.
- Version framework nên được quản lý tập trung bằng `lsf.version`.
- Cách consume chuẩn là import BOM từ `lsf-parent` thay vì pin version rải rác ở từng dependency.
- Module được dùng theo concern, không theo kiểu “kéo hết starter”.
- Chỉ public surface area được mô tả ở tài liệu này mới nên được coi là contract tương đối ổn định.

## 3. Concern Map: module nào giải quyết concern gì

| Concern | Modules chính | Khi nên dùng |
|---|---|---|
| shared contracts | `lsf-contracts` | khi cần chuẩn hóa `EventEnvelope`, trace/request context, retry semantics, sync error model |
| Kafka runtime conventions | `lsf-kafka-starter` | khi service publish/consume Kafka và muốn thống nhất retry, DLQ, serializer, header conventions |
| event dispatch/publish API | `lsf-eventing-starter` | khi muốn dùng handler-style dispatch và `LsfPublisher` thay cho wiring Kafka ad hoc |
| event observability | `lsf-observability-starter` | khi service đã dùng eventing và cần metrics/MDC/observation quanh dispatcher |
| durable publish sau transaction | `lsf-outbox-core`, `lsf-outbox-mysql-starter`, `lsf-outbox-postgres-starter` | khi cần tránh dual-write giữa DB commit và event publish |
| quota / reservation | `lsf-quota-starter` | khi service có concern reserve-confirm-release cho tài nguyên hữu hạn |
| sync HTTP ingress | `lsf-service-web-starter` | khi cần chuẩn hóa REST ingress, request context, error response |
| sync HTTP egress | `lsf-http-client-starter` | khi muốn dùng declarative HTTP client theo service id |
| discovery / config / resilience / security foundation | `lsf-discovery-starter`, `lsf-config-starter`, `lsf-resilience-starter`, `lsf-security-starter` | khi service cần foundation modules để hỗ trợ sync/runtime conventions |
| gateway conventions | `lsf-gateway-starter` | khi có internal or edge gateway đơn giản cần conventions theo LSF |
| orchestration | `lsf-saga-starter` | khi workflow đủ khớp với model sequential saga của repo hiện tại |
| internal admin tooling | `lsf-outbox-admin-starter`, `lsf-kafka-admin-starter` | khi service cần endpoint nội bộ để inspect/requeue/replay |
| adoption scaffold | `lsf-service-template`, `lsf-example` | khi cần bootstrap service mới hoặc xem reference composition |

## 4. Khi adopter không nên dùng module như production default

Các trường hợp sau cần nêu rõ để tránh over-adoption:

- Không dùng `lsf-example` như template production có thể deploy nguyên trạng.
- Không dùng `lsf-service-template` như business service hoàn chỉnh; đây là scaffold để copy/adapt.
- Không mở `lsf-outbox-admin-starter` và `lsf-kafka-admin-starter` như public endpoints mặc định.
- Không coi `lsf-gateway-starter` là org-wide gateway standard nếu chưa có owner review.
- Không coi `lsf-saga-starter` là workflow engine tổng quát cho graph phức tạp.
- Không mặc định chọn `lsf-outbox-postgres-starter` nếu tổ chức chưa validate riêng PostgreSQL path.
- Không giả định sync stack (`lsf-service-web-starter` + `lsf-http-client-starter` + foundation modules) đã có checkpoint rộng như event-driven stack hiện tại.

## 5. Official Public Surface Area

Các bề mặt dưới đây là public surface area mà adopter có thể dựa vào khi tích hợp:

### 5.1 Maven surface

- GroupId `com.myorg.lsf`
- Reactor module artifactIds hiện có trong `lsf-parent`
- BOM import qua `lsf-parent`

### 5.2 Configuration surface

Các property prefixes đã được code/document hóa và nên được xem là public:

- `lsf.config.*`
- `lsf.discovery.*`
- `lsf.gateway.*`
- `lsf.security.*`
- `lsf.resilience.*`
- `lsf.service.web.*`
- `lsf.http.client.*`
- `lsf.kafka.*`
- `lsf.kafka.admin.*`
- `lsf.eventing.*`
- `lsf.observability.*`
- `lsf.outbox.*`
- `lsf.outbox.admin.*`
- `lsf.quota.*`
- `lsf.saga.*`

### 5.3 Runtime API surface

Các API chính nên được xem là adopter-facing:

- `EventEnvelope`, `EnvelopeBuilder`, `LsfRequestContext`, `LsfTraceContext`, `LsfErrorResponse`
- `LsfRetryAware`, `LsfRetryableException`, `LsfNonRetryableException`
- `LsfPublisher`, `LsfDispatcher`, `LsfPublishOptions`, `@LsfEventHandler`
- `LsfServiceLocator`
- `@LsfHttpClient`, `LsfClientAuthMode`
- `LsfResilienceExecutor`
- `OutboxWriter`
- `QuotaService`, `QuotaReservationFacade`, `QuotaQueryFacade`, `QuotaPolicyProvider`
- `LsfSagaOrchestrator` và saga contracts đi kèm, nhưng cần hiểu đây là public API của một module `partial support`

### 5.4 HTTP admin surface

Khi admin starters được bật, các base paths sau là public operational surface nội bộ:

- `lsf-outbox-admin-starter`: `${lsf.outbox.admin.base-path:/lsf/outbox}`
- `lsf-kafka-admin-starter`: `${lsf.kafka.admin.base-path:/lsf/kafka}`

### 5.5 Documentation surface

Các tài liệu sau là public adoption contract:

- `README.md`
- `docs/PLATFORM_ADOPTION.md`
- `docs/MODULE_MATURITY.md`
- `docs/COMPATIBILITY.md`
- `docs/UPGRADING.md`
- `docs/RELEASE_POLICY.md`
- `docs/GOLDEN_PATHS.md`
- module README files tương ứng

## 6. Những gì không thuộc public contract

Các phần dưới đây không nên được adopter coi là compatibility promise:

- bean names nội bộ, wiring chi tiết trong auto-configuration;
- helper classes nội bộ không được docs nhắc tới như adoption surface;
- chi tiết implementation của `lsf-example` và `lsf-service-template`;
- default values trong `ops/`, Helm skeleton, Docker Compose nếu chưa được owner chốt như supported baseline;
- internal package layout bên trong từng starter;
- test fixtures và test-only configuration.

## 7. Adoption Rules Cho Team Và Codex

- Ưu tiên golden path trước, rồi mới mở rộng sang partial modules.
- Mỗi rollout nên gắn với một concern rõ ràng: contracts, Kafka, outbox, quota, sync HTTP, admin tooling.
- Không merge framing “platform hoàn chỉnh” vào docs nếu capability chưa có bằng chứng.
- Khi dùng partial modules, phải thêm note vào service docs/PR về scope được support.
- Nếu service chỉ cần một concern, chỉ thêm module cho concern đó.

## 8. Compatibility Note Với Consumer Ecommerce Hiện Tại

Theo trạng thái code hiện tại, consumer repo [lsf-ecommerce-backend](https://github.com/truongnguyen3006/lsf-ecommerce-backend.git) đang dùng các module LSF sau:

- `lsf-contracts`
- `lsf-kafka-starter`
- `lsf-eventing-starter`
- `lsf-observability-starter`
- `lsf-outbox-mysql-starter`
- `lsf-outbox-admin-starter`
- `lsf-kafka-admin-starter`
- `lsf-quota-starter`
- `lsf-saga-starter`

Điều này cho thấy các concern Kafka/eventing, observability, outbox MySQL, bằng chứng quản trị, quota/reservation và checkout saga đã có bằng chứng consumer rõ hơn. Tuy vậy, nó không đồng nghĩa toàn bộ module khác của LSF đã được consumer hiện tại xác thực.

## Ghi chú adoption saga từ consumer evidence

- `lsf-saga-starter` vẫn nên được adoption trước như một cơ chế điều phối tuần tự.
- Public contract hiện tại có thêm nhóm helper hẹp cho adapter do consumer sở hữu:
  - `SagaReplyFanInSession`
  - `SagaReplyFanInSignal`
  - `SagaReplyFanInUpdate`
  - `SagaReplyFanInSupport`
- Nhóm helper này phù hợp khi một saga step cần chờ nhiều downstream replies cấp thấp và gom lại thành một kết quả success/failure ở cấp order trước khi saga đi tiếp.
- Điều này không thay đổi non-goal: LSF saga vẫn không phải workflow engine tổng quát cho mọi dạng branch/join graph.
- Khi một service có thể giữ fan-out/fan-in ở nội bộ và chỉ trả một order-level reply về saga, đó là hướng được ưu tiên hiện tại.

## 9. Owner Decisions Needed

- Chốt danh sách “blessed modules” cho các phase rollout tiếp theo: chỉ stable core hay cả một phần partial support.
- Chốt liệu admin starters có được xem là platform-supported cho production nội bộ hay chỉ là convenience modules.
- Chốt deadline để consumer ecommerce chuyển từ `dependencyManagement` từng artifact sang BOM import `lsf-parent`.
