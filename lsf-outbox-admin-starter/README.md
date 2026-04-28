# lsf-outbox-admin-starter

> Starter cung cấp REST API nội bộ để vận hành outbox: list, inspect, requeue, mark failed và delete có kiểm soát.

Module này giúp demo/vận hành nhìn thấy trạng thái outbox mà không phải truy vấn database thủ công.

## Dùng khi nào?

- Service đã bật outbox runtime và cần admin surface.
- Cần kiểm tra event pending/retry/failed khi demo hoặc debug.
- Cần requeue event lỗi sau khi fix nguyên nhân.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-admin-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

```yaml
lsf:
  outbox:
    admin:
      enabled: true
      base-path: /admin/outbox
      default-limit: 50
      max-limit: 200
      allow-retry: true
      allow-delete: false
```

## Endpoints

| Method | Path | Mục đích |
|---|---|---|
| `GET` | `/admin/outbox` | List rows, filter theo status/topic/msgKey/eventType/correlationId/from/to |
| `GET` | `/admin/outbox/{id}` | Xem row theo id |
| `GET` | `/admin/outbox/event/{eventId}` | Xem row theo event id |
| `POST` | `/admin/outbox/requeue/event/{eventId}` | Requeue một event |
| `POST` | `/admin/outbox/requeue/failed` | Requeue nhiều failed rows |
| `POST` | `/admin/outbox/mark-failed/event/{eventId}` | Mark một event là failed |
| `DELETE` | `/admin/outbox/event/{eventId}` | Xóa event nếu `allow-delete=true` |

## Lưu ý bảo mật

- Không expose admin endpoint công khai.
- Nên dùng `lsf-security-starter`, gateway rule hoặc network policy để khóa endpoint.
- `allow-delete=false` là mặc định an toàn; chỉ bật khi có quy trình vận hành rõ ràng.

## Trạng thái

- Có regression cho MySQL/PostgreSQL ở mức framework.
- Phù hợp làm evidence/admin tool cho đồ án và môi trường nội bộ.
