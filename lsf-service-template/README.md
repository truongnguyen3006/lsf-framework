# lsf-service-template

> Service scaffold để tạo microservice mới trên nền LSF.

Module này không phải business service hoàn chỉnh. Nó là mẫu tham khảo cho cách tổ chức package, khai báo dependency, expose internal API, publish/consume event, gọi downstream HTTP và tham gia workflow.

## Dùng khi nào?

- Muốn tạo service mới theo convention LSF.
- Muốn xem một skeleton đã nối sẵn web, security, eventing, outbox, HTTP client và saga workflow.
- Muốn copy/adapt cấu trúc project thay vì bắt đầu từ Spring Initializr trống.

## Capability minh họa

| Capability | Module/Thành phần |
|---|---|
| REST internal API | `lsf-service-web-starter` |
| Request/trace propagation | `lsf-contracts`, `lsf-service-web-starter` |
| HTTP client | `lsf-http-client-starter` |
| Event publishing | `lsf-eventing-starter`, `lsf-kafka-starter` |
| Event handling | `@LsfEventHandler` |
| Outbox runtime | profile MySQL/PostgreSQL |
| Workflow participant | `lsf-saga-starter` |

## Chạy template service

Từ root repo:

```bash
git clone https://github.com/truongnguyen3006/lsf-framework.git
cd lsf-framework
mvn -pl lsf-service-template -am spring-boot:run
```

Chạy với profile Docker:

```bash
mvn -pl lsf-service-template -am spring-boot:run -Dspring-boot.run.profiles=docker
```

Chạy với outbox MySQL:

```bash
mvn -pl lsf-service-template -am spring-boot:run -Dspring-boot.run.profiles=outbox-mysql
```

Chạy với outbox PostgreSQL:

```bash
mvn -pl lsf-service-template -am spring-boot:run -Dspring-boot.run.profiles=outbox-postgres
```

## Endpoint mẫu

| Method | Path | Mục đích |
|---|---|---|
| `GET` | `/internal/template/context` | Xem request context hiện tại |
| `GET` | `/internal/template/dependency/capabilities` | Gọi downstream dependency qua HTTP client |
| `POST` | `/internal/template/work-items` | Tạo work item mẫu và publish integration event |

## Cấu trúc thư mục

```text
lsf-service-template/
├─ src/main/java/com/myorg/lsf/template/
│  ├─ api/                 # REST controllers và DTO API
│  ├─ application/         # Business service mẫu
│  ├─ config/              # Properties/config riêng của template
│  ├─ integration/http/    # HTTP client và gateway tới dependency
│  ├─ messaging/           # Event types, publisher, handlers
│  ├─ support/             # Request metadata helpers
│  └─ workflow/            # Workflow participant handlers
└─ src/main/resources/
   ├─ application.yml
   ├─ application-docker.yml
   ├─ application-outbox-mysql.yml
   └─ application-outbox-postgres.yml
```

## Cấu hình riêng của template

```yaml
template:
  service:
    integration-topic: template.integration.events
    integration-event-type: template.resource.requested.v1
    workflow-reply-topic: template.workflow.replies
    workflow-completed-event-type: template.workflow.step.completed.v1
```

## Cách dùng làm scaffold

1. Copy module hoặc tạo module mới dựa trên cấu trúc này.
2. Đổi package `com.myorg.lsf.template` thành package service thật.
3. Đổi `artifactId`, `spring.application.name`, topic names và event types.
4. Xóa controller/DTO demo không cần thiết.
5. Giữ các starter LSF đúng với nhu cầu service.
6. Viết README riêng cho service mới, mô tả business flow cụ thể.

## Lưu ý

- Template là tài sản adoption, không phải product runtime độc lập.
- Không nên giữ nguyên API demo khi đưa vào business service thật.
- Secret, API key và endpoint production phải được cấu hình ngoài source code.
