# Module Maturity

Tài liệu này audit toàn bộ reactor modules hiện có trong `lsf-parent` và phân loại chúng theo 3 mức:

- `stable`: đã có contract áp dụng rõ ràng, là public building block nên adopter có thể dùng như baseline cho concern tương ứng.
- `partial support`: có capability thật và có thể dùng có kiểm soát, nhưng chưa nên coi là default path cho mọi service.
- `experimental / scaffold`: chủ yếu để minh họa, bootstrap hoặc thử nghiệm adoption path; không phải runtime default.

`lsf-parent` là BOM / governance parent POM, không được tính như runtime module nên không nằm trong các bucket dưới đây.

## Tiêu chí phân loại

- Bằng chứng từ source code hiện có trong repo.
- Độ phủ test và module README hiện có.
- Mức độ được dùng trong consumer evidence tại [lsf-ecommerce-backend](https://github.com/truongnguyen3006/lsf-ecommerce-backend.git).
- Mức độ framework có thể bảo vệ public contract mà không overclaim production completeness.

## Stable

| Module | Concern chính | Vì sao đang ở mức stable | Guidance cho adopter |
|---|---|---|---|
| `lsf-contracts` | shared contracts, envelope, request/trace context, retry model, sync error model | là core library được nhiều starter dùng lại và đang là phần contract chung trong consumer ecommerce | dùng khi muốn chuẩn hóa payload/event/error thay vì tự duy trì DTO + headers conventions riêng |
| `lsf-kafka-starter` | Kafka producer/consumer baseline, retry/DLQ, serializer conventions | có focused tests, là nền cho eventing, và đang nằm trong compatibility checkpoint của ecommerce | dùng khi service cần Kafka runtime conventions chung; đây là Kafka baseline mặc định của LSF |
| `lsf-eventing-starter` | handler-style dispatch, publisher API, idempotency path | có focused tests, có cross-module runtime test với `lsf-kafka-starter` + `lsf-observability-starter`, và đang được consumer dùng | dùng khi muốn bỏ `@KafkaListener` ad hoc và chuyển sang handler contract của framework |
| `lsf-observability-starter` | metrics/MDC/observation quanh event dispatch | có focused tests, có cross-module runtime evidence cho metadata/metrics/observation, và đang được consumer dùng | dùng khi service đã dùng `lsf-eventing-starter` và cần observability mức framework |
| `lsf-outbox-core` | abstraction `OutboxWriter` và contract append event sau transaction | là lõi chung cho cả MySQL/PostgreSQL runtime, public surface nhỏ và rõ | dùng khi service cần dual-write mitigation; không dùng độc lập nếu chưa chọn runtime DB |
| `lsf-outbox-mysql-starter` | MySQL outbox writer + publisher poller | là durable publish path đang gần nhất với use case ecommerce hiện tại; có tests và đang nằm trong compatibility checkpoint | dùng khi service chạy MySQL và cần publish event sau transaction database |
| `lsf-quota-starter` | reserve / confirm / release cho tài nguyên hữu hạn | có runtime thật, có test, và đã đi qua compatibility checkpoint ở consumer inventory flow | dùng khi service có concern quota / oversell / reservation; không cần tự dựng quota engine riêng trước khi vượt quá capability hiện tại |

## Partial Support

| Module | Concern chính | Vì sao mới ở mức partial support | Guidance cho adopter |
|---|---|---|---|
| `lsf-config-starter` | chuẩn hóa import config theo mode | capability có thật nhưng chưa phải baseline đã được consumer checkpoint rộng | dùng khi team muốn gom `spring.config.import` conventions; nên validate riêng với config source thật |
| `lsf-discovery-starter` | service lookup qua `DiscoveryClient` hoặc static registry | có auto-config và test, nhưng chưa phải adoption path đã được consumer dùng rộng | dùng khi service cần lookup nhất quán; không coi đây là service registry product hoàn chỉnh |
| `lsf-gateway-starter` | Spring Cloud Gateway conventions | module nhỏ, có giá trị framework-level nhưng chưa có bằng chứng adoption rộng | chỉ dùng cho internal/edge gateway đơn giản; không nên tự động chọn làm org-wide gateway default |
| `lsf-http-client-starter` | declarative HTTP client qua `@LsfHttpClient` + `@HttpExchange` | đã có cross-module runtime evidence với `lsf-service-web-starter` + `lsf-security-starter` + `lsf-resilience-starter` cho success path, header propagation và retry/non-retryable decode; tuy vậy vẫn chưa nằm trong ecommerce checkpoint hiện tại | dùng khi service cần sync HTTP path của LSF; nên rollout có kiểm soát cùng auth/discovery/resilience thật |
| `lsf-kafka-admin-starter` | inspect / replay DLQ records | đã có broker-backed Testcontainers regression cho read/replay/headers/metrics, nhưng bản chất vẫn là ops/admin surface opt-in | chỉ bật cho internal operations; không mở public internet-facing by default |
| `lsf-outbox-admin-starter` | list / requeue / mark-failed / delete outbox rows | đã có vendor-specific MySQL/PostgreSQL regression cho các DB-sensitive paths, nhưng bản chất vẫn là internal admin tooling | chỉ bật cho internal/admin path có auth và governance; không coi là default endpoint phải mở ở mọi service |
| `lsf-outbox-postgres-starter` | PostgreSQL outbox runtime | capability tồn tại nhưng chưa là path đã được consumer checkpoint | dùng khi service thực sự chạy PostgreSQL và đã có validate riêng trên schema / polling strategy thật |
| `lsf-resilience-starter` | retry / timeout / circuit breaker / rate limit executor | có focused policy tests và đã có cross-module runtime evidence cho retry classification trên sync HTTP path, nhưng chưa là default path đã được consumer dùng thực chiến | dùng khi sync path cần policy tập trung; không coi là policy platform hoàn chỉnh thay thế mọi resilience concern |
| `lsf-saga-starter` | event-driven orchestration / compensation | đã có runtime integration evidence cho sequential direct-transport path với JDBC store, success path và compensation path tối thiểu; tuy vậy scope vẫn thiên về sequential orchestration và chưa cover outbox transport | chỉ dùng khi workflow đủ khớp với model hiện có; không diễn giải như workflow engine tổng quát |
| `lsf-security-starter` | API key / JWT baseline | có servlet integration tests riêng và đã có cross-module runtime evidence cho API key path trong sync HTTP flow, nhưng chưa nên coi là full security platform | dùng khi service cần auth bootstrap nhất quán; vẫn cần owner chốt auth model thực tế |
| `lsf-service-web-starter` | HTTP ingress conventions, request context, error model | có servlet integration tests riêng và đã có cross-module runtime evidence với HTTP client/security/resilience cho canonical headers và `LsfErrorResponse`, nhưng vẫn thiên về servlet path và chưa nằm trong compatibility checkpoint rộng | dùng khi service cần sync REST ingress chuẩn hóa; nên rollout cùng HTTP client/discovery/resilience nếu muốn full sync path |

## Experimental / Scaffold

| Module | Concern chính | Vì sao ở mức experimental / scaffold | Guidance cho adopter |
|---|---|---|---|
| `lsf-example` | example app ghép nhiều starter | là app minh họa capability composition, không phải runtime library để service phụ thuộc trực tiếp như production default | dùng để học cách ghép module và làm testbed local; không copy nguyên trạng vào production |
| `lsf-service-template` | copyable service scaffold | mục tiêu là bootstrap structure và adoption path, không phải service runtime thành phẩm | dùng làm khung khởi tạo service mới rồi chỉnh sâu; không deploy nguyên module này như business service thực |

## Non-reactor Assets Liên Quan Adoption

Các asset sau không phải reactor module nhưng ảnh hưởng trực tiếp tới adoption framing:

- `ops/`: scaffold cho operations và monitoring baseline.
- `ops/deployment/`: deployment skeleton, Docker/Helm guidance.
- `docker-compose.yml`: local validation baseline.
- `.github/workflows/`: CI/CD skeleton cho repo framework.

Nhóm asset này nên được hiểu là `scaffold`, không phải tuyên bố rằng LSF đã có production platform hoàn chỉnh.

## Kết luận Audit

- Hiện tại LSF có một stable core khá rõ cho event-driven contracts, Kafka conventions, observability, MySQL outbox và quota; eventing/observability cũng vừa có thêm cross-module runtime evidence.
- Sync HTTP, security, discovery, gateway, admin tooling và saga là các capability có thật nhưng nên rollout có kiểm soát theo từng concern; riêng sync HTTP stack vừa tăng evidence đáng kể nhờ cross-module runtime test nhưng vẫn nên giữ `partial support`.
- `lsf-saga-starter` đã tăng evidence runtime nhờ integration test mới cho direct transport + JDBC, nhưng vẫn nên giữ ở `partial support` cho tới khi có outbox transport coverage và validation rộng hơn.
- Kafka admin và outbox admin vẫn được giữ ở `partial support`, nhưng evidence test cho hai module này đã tăng rõ rệt nhờ broker-backed và vendor-specific regression mới.
- Example, template và ops assets là adoption aids; chúng quan trọng cho Phase 1 nhưng không nên bị diễn giải thành runtime promises.

## Cập nhật bằng chứng saga

- `lsf-saga-starter` vẫn ở mức `partial support`.
- Bằng chứng từ consumer [lsf-ecommerce-backend](https://github.com/truongnguyen3006/lsf-ecommerce-backend.git) tăng độ tin cậy cho:
  - sequential success and compensation flows
  - timeout handling
  - duplicate and late reply handling
  - JDBC-backed resume of a persisted waiting saga
  - a narrow public helper for local reply fan-in before the saga advances
- Cùng bằng chứng đó cũng xác nhận giới hạn hiện tại:
  - multi-SKU fan-out/join is still best handled by a local consumer adapter, not by expanding the starter into a general workflow engine
- Vì giới hạn này, và vì outbox-backed saga transport chưa phải runtime path có bằng chứng mạnh nhất, maturity chưa được nâng lên `stable` trong phase này.

## Owner Decisions Needed

- Xác định rõ tiêu chí nâng `partial support` lên `stable`: cần consumer checkpoint, focused regression hay cả end-to-end validation.
- Chốt `lsf-outbox-admin-starter` và `lsf-kafka-admin-starter` có được xem là “supported internal production tooling” hay chỉ là optional convenience modules.
- Chốt liệu `lsf-outbox-postgres-starter` có cần một compatibility checkpoint riêng trước khi được khuyến nghị ngang hàng với MySQL path hay không.
