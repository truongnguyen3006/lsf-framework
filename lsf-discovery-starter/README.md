# lsf-discovery-starter

> Starter cung cấp `LsfServiceLocator` để service và HTTP client tìm downstream service theo một API thống nhất.

Module này có hai hướng dùng chính: tận dụng Spring `DiscoveryClient` nếu môi trường đã có Eureka/Consul/Kubernetes discovery, hoặc dùng static discovery cho local/dev/test.

## Dùng khi nào?

- Service cần gọi downstream bằng `serviceId` thay vì hard-code URL.
- Local/test chưa muốn dựng discovery server đầy đủ.
- Module khác như `lsf-http-client-starter` cần một abstraction ổn định để resolve service URI.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-discovery-starter</artifactId>
</dependency>
```

## Cấu hình static discovery

```yaml
lsf:
  discovery:
    enabled: true
    mode: STATIC
    services:
      inventory-service:
        - host: localhost
          port: 8082
          secure: false
      payment-service:
        - host: localhost
          port: 8089
          context-path: /api
```

## Thuộc tính chính

| Property | Ý nghĩa | Mặc định |
|---|---|---|
| `lsf.discovery.enabled` | Bật discovery support | `true` |
| `lsf.discovery.mode` | `AUTO`, `STATIC`, `REQUIRED` hoặc `DISABLED` | `AUTO` |
| `lsf.discovery.services` | Danh sách service instance tĩnh | rỗng |

## API chính

```java
URI uri = serviceLocator.resolve("inventory-service")
    .orElseThrow();
```

`LsfServiceLocator` được dùng nội bộ bởi `lsf-http-client-starter`, nhưng service application cũng có thể inject trực tiếp nếu cần.

## Cấu trúc module

```text
lsf-discovery-starter/
├─ LsfDiscoveryAutoConfiguration.java
├─ LsfDiscoveryProperties.java
├─ LsfServiceLocator.java
├─ LsfStaticDiscoveryClient.java
└─ LsfStaticReactiveDiscoveryClient.java
```

## Lưu ý

- Module này không thay thế Eureka/Kubernetes discovery; nó là lớp convention/adapter.
- Với production, nên dùng discovery runtime thật và cấu hình health check phù hợp.
- Static discovery rất hữu ích cho integration test và demo local.
