# lsf-http-client-starter

> Starter tạo HTTP client nội bộ trên Spring `RestClient`, có discovery, resilience và auth/context propagation.

Module này giúp service gọi downstream REST API bằng interface có annotation, thay vì tự dựng URL, header, retry và error handling ở từng nơi.

## Dùng khi nào?

- Service cần gọi REST tới service khác trong cùng hệ microservices.
- Muốn dùng `serviceId` thay vì hard-code host/port.
- Muốn propagate trace/request headers và auth token/API key.
- Muốn áp dụng resilience policy theo từng client.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-http-client-starter</artifactId>
</dependency>
```

## Khai báo client

```java
@EnableLsfHttpClients(basePackages = "com.example.clients")
@SpringBootApplication
class OrderApplication {
}
```

```java
@LsfHttpClient(
    serviceId = "inventory-service",
    pathPrefix = "/api/inventory",
    resilienceId = "inventory-client"
)
public interface InventoryClient {

    @GetExchange("/{sku}/availability")
    InventoryAvailabilityResponse availability(@PathVariable String sku);
}
```

## Cấu hình mẫu

```yaml
lsf:
  http:
    client:
      enabled: true
      connect-timeout: 1s
      read-timeout: 2s
      authentication:
        mode: AUTO
        api-key:
          header-name: X-API-Key
          value: local-dev-secret
  discovery:
    mode: STATIC
    services:
      inventory-service:
        - host: localhost
          port: 8082
  resilience:
    instances:
      inventory-client:
        retry:
          enabled: true
          max-attempts: 2
```

## Thành phần chính

| Class/annotation | Vai trò |
|---|---|
| `@EnableLsfHttpClients` | Scan và đăng ký client interfaces |
| `@LsfHttpClient` | Khai báo `serviceId`, `pathPrefix`, `resilienceId`, `authMode` |
| `LsfHttpServiceClientFactory` | Tạo proxy client |
| `LsfServiceUriBuilderFactory` | Resolve base URI từ discovery |
| `LsfRemoteServiceException` | Chuẩn hóa lỗi từ downstream |
| `LsfRequestContextPropagationInterceptor` | Propagate request context |
| `LsfAuthenticationInterceptor` | Propagate API key/bearer token |

## Lưu ý

- Module này dùng servlet `RestClient`, chưa phải WebClient/reactive client đầy đủ.
- Downstream nên dùng `lsf-service-web-starter` để error response decode nhất quán.
- Retry chỉ an toàn khi API downstream idempotent hoặc có idempotency key.
