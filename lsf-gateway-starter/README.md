# lsf-gateway-starter

> Starter bổ sung convention nhẹ cho Spring Cloud Gateway: route declaration và correlation/request id propagation.

Module này phù hợp khi bạn muốn gateway của hệ LSF có cách khai báo route thống nhất, đồng thời tự sinh hoặc echo các header phục vụ tracing.

## Dùng khi nào?

- Hệ thống có API Gateway trên Spring Cloud Gateway.
- Cần route request vào các service nội bộ theo convention đơn giản.
- Muốn gateway luôn có `X-Correlation-Id` và request id để log/tracing dễ theo dõi.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-gateway-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

```yaml
lsf:
  gateway:
    enabled: true
    correlation-header: X-Correlation-Id
    generate-correlation-id: true
    echo-correlation-id-response: true
    generate-request-id: true
    echo-request-id-response: true
    routes:
      - id: product-service
        path: /api/product/**
        uri: lb://product-service
      - id: order-service
        path: /api/order/**
        uri: lb://order-service
```

## Thuộc tính chính

| Property | Ý nghĩa | Mặc định |
|---|---|---|
| `lsf.gateway.enabled` | Bật auto-configuration | `true` |
| `lsf.gateway.correlation-header` | Header correlation id | `X-Correlation-Id` |
| `lsf.gateway.generate-correlation-id` | Tự sinh correlation id nếu request chưa có | `true` |
| `lsf.gateway.echo-correlation-id-response` | Ghi lại correlation id ra response | `true` |
| `lsf.gateway.routes` | Danh sách route LSF | rỗng |

## Cung cấp gì?

- `LsfCorrelationIdGlobalFilter`
- Auto-configuration cho route convention
- Hỗ trợ thêm request/response headers theo từng route
- Tùy chọn `stripPrefix` cho route đơn giản

## Lưu ý

- Module này không thay thế toàn bộ cấu hình Spring Cloud Gateway gốc.
- Với gateway production, vẫn cần cấu hình rate limit, CORS, security, timeout và observability theo nhu cầu riêng.
