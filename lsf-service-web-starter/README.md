# lsf-service-web-starter

> Starter chuẩn hóa HTTP ingress cho servlet service: request context, trace headers và error response.

Module này giúp REST service nội bộ có cùng cách nhận/ghi header, sinh correlation id/request id và trả lỗi dạng `LsfErrorResponse`.

## Dùng khi nào?

- Service exposes REST API nội bộ hoặc public API cần format lỗi thống nhất.
- Cần propagate `correlation-id`, `causation-id`, `request-id` qua nhiều service.
- Muốn giảm boilerplate `@ControllerAdvice` và filter hạ tầng.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-service-web-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

```yaml
lsf:
  service:
    web:
      enabled: true
      generate-correlation-id: true
      generate-request-id: true
      echo-headers: true
```

## Cung cấp gì?

| Class | Vai trò |
|---|---|
| `LsfRequestContextFilter` | Đọc/sinh request context và bind vào holder |
| `LsfHttpExceptionHandler` | Chuyển exception thành `LsfErrorResponse` |
| `LsfErrorResponseFactory` | Tạo error body thống nhất |
| `LsfErrorResponseWriter` | Ghi error body trong filter/security flow |

## Response lỗi mẫu

```json
{
  "timestamp": "2026-04-28T10:15:30Z",
  "status": 404,
  "error": "Not Found",
  "message": "Resource not found",
  "path": "/api/order/ORD-001",
  "correlationId": "8c6f..."
}
```

## Lưu ý

- Module này tập trung vào servlet stack (`spring-boot-starter-web`).
- Với WebFlux gateway, dùng cấu hình riêng hoặc `lsf-gateway-starter`.
- Nên dùng kèm `lsf-http-client-starter` để context được propagate khi gọi downstream.
