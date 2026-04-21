# lsf-outbox-admin-starter

Starter này cung cấp REST API để vận hành outbox ở mức framework: list/filter rows, inspect theo id hoặc event id, requeue, mark failed và delete có kiểm soát.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-admin-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Cấu hình bật starter

```yaml
lsf:
  outbox:
    admin:
      enabled: true
      base-path: /lsf/outbox
      default-limit: 50
      max-limit: 200
      allow-retry: true
      allow-delete: false
```

## Endpoints hiện có

- `GET /lsf/outbox`
- `GET /lsf/outbox/{id}`
- `GET /lsf/outbox/event/{eventId}`
- `POST /lsf/outbox/requeue/event/{eventId}?mode=RETRY&resetRetry=true`
- `POST /lsf/outbox/requeue/failed?limit=50&resetRetry=true`
- `POST /lsf/outbox/mark-failed/event/{eventId}`
- `DELETE /lsf/outbox/event/{eventId}`

`GET /lsf/outbox` hiện hỗ trợ filter theo:

- `status`
- `topic`
- `msgKey`
- `eventType`
- `correlationId`
- `from`
- `to`
- `limit`
- `offset`

## Ghi chú thiết kế

- `allow-delete=false` theo mặc định để tránh xóa nhầm dữ liệu vận hành.
- `allow-retry=false` có thể dùng khi chỉ muốn inspect chứ chưa cho phép operator requeue.
- Repository hiện dùng JDBC và bám theo schema outbox của runtime modules.

## Validation hiện có

- vendor-specific MySQL/PostgreSQL regression cho các path DB-sensitive ở repository/service
- suite mới verify list/filter, inspect theo id hoặc event id, requeue failed, mark failed và delete guardrails
- migration được dùng lại từ các runtime modules để tránh drift schema giữa runtime và admin tooling

## Khuyến nghị sử dụng

- Chỉ expose endpoint này trên internal network hoặc sau auth/admin role.
- Dùng nó như tooling hỗ trợ điều tra và remediation, không phải thay thế monitoring hay playbook vận hành đầy đủ.

## Giới hạn hiện tại

- module này tập trung vào outbox rows hiện có; chưa có bulk workflow hay dashboard UI đi kèm
- vendor-specific regression hiện tập trung vào repository/service path trọng yếu, chưa phải full container-backed coverage cho mọi controller path
