# lsf-security-starter

> Starter cung cấp security baseline cho servlet service: API key nội bộ hoặc JWT resource server.

Module này giúp các service LSF bảo vệ nhanh internal/admin endpoints mà không phải lặp lại cấu hình Spring Security cơ bản.

## Dùng khi nào?

- Service có admin endpoint như outbox/kafka admin cần khóa lại.
- Internal service-to-service call cần API key đơn giản.
- Service muốn dùng JWT với `issuer-uri`, `jwk-set-uri` hoặc HMAC secret.
- Dự án muốn chuẩn hóa public paths và admin authorities giữa nhiều service.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-security-starter</artifactId>
</dependency>
```

## Cấu hình API key

```yaml
lsf:
  security:
    enabled: true
    mode: API_KEY
    public-paths:
      - /actuator/health
      - /swagger-ui/**
    admin-paths:
      - /admin/**
    api-key:
      header-name: X-API-Key
      value: local-dev-secret
      principal: lsf-internal
      authorities:
        - ROLE_LSF_INTERNAL
```

## Cấu hình JWT

```yaml
lsf:
  security:
    enabled: true
    mode: JWT
    jwt:
      issuer-uri: http://localhost:8085/realms/spring-boot-microservices-realm
      authorities-claim: scope
      authority-prefix: SCOPE_
```

## Thuộc tính chính

| Property | Ý nghĩa | Mặc định |
|---|---|---|
| `lsf.security.enabled` | Bật security auto-config | `false` |
| `lsf.security.mode` | `API_KEY` hoặc `JWT` | `API_KEY` |
| `lsf.security.public-paths` | Endpoint cho phép public | actuator health/info mặc định |
| `lsf.security.admin-paths` | Endpoint yêu cầu quyền admin | `/admin/**` |
| `lsf.security.admin-authorities` | Quyền admin hợp lệ | `ROLE_LSF_ADMIN` |

## Lưu ý

- `enabled=false` mặc định để tránh khóa nhầm service khi chỉ thêm dependency.
- Không commit API key thật vào repository.
- Module hiện tập trung vào servlet-based services, không phải WebFlux gateway security đầy đủ.
