# lsf-quota-streams-starter

Module này đóng gói bài toán **quota / reservation** cho các tình huống large-scale như:
- flash sale / oversell control
- booking / giữ chỗ tạm thời
- đăng ký học phần / slot giới hạn
- inventory hold trước khi thanh toán hoàn tất

## Module giải quyết vấn đề gì?

Trong hệ microservices lớn, nhiều request có thể cùng lúc tranh chấp một tài nguyên giới hạn. Nếu chỉ dùng CRUD đơn giản thì rất dễ gặp:
- oversell / overbooking
- giữ chỗ nhưng không tự hết hạn
- xử lý trùng request
- logic reserve / confirm / release không thống nhất giữa các service

Starter này chuẩn hóa lại flow quota theo 3 thao tác:
- `reserve(quotaKey, requestId, amount)`
- `confirm(quotaKey, requestId)`
- `release(quotaKey, requestId)`

## Hai lớp chính

### 1. `QuotaReservationFacade`
Facade ở tầng business. Nhiệm vụ của nó là:
- lấy policy theo `quotaKey`
- áp `limit` + `hold`
- gọi `QuotaService` backend

### 2. `QuotaService`
Backend thực thi quota:
- `MemoryQuotaService`: phù hợp dev/test hoặc single-instance
- `RedisQuotaService`: phù hợp multi-instance / distributed runtime

## Policy provider

Starter hỗ trợ 3 cách cấp policy:
- `STATIC`: đọc từ `lsf.quota.policies` trong YAML
- `JDBC`: đọc từ bảng `quota_policy`
- `AUTO`: ưu tiên JDBC nếu có `JdbcTemplate`, không thì fallback STATIC

Ngoài ra policy có thể cache bằng:
- `NONE`
- `MEMORY`
- `REDIS`
- `MEMORY_REDIS`

## Cấu hình ví dụ

```yaml
lsf:
  quota:
    enabled: true
    store: redis
    key-prefix: "lsf:quota:"
    default-hold-seconds: 30
    keep-alive-seconds: 86400
    allow-release-confirmed: false
    provider:
      mode: JDBC
      jdbc:
        table: quota_policy
        enabled-only: true
      cache:
        mode: MEMORY_REDIS
        ttl-seconds: 30
        local-max-size: 10000
        redis-prefix: "lsf:quota:policy:"
```

## Bảng JDBC mẫu

```sql
CREATE TABLE quota_policy (
  quota_key     VARCHAR(255) PRIMARY KEY,
  quota_limit   INT NOT NULL,
  hold_seconds  INT NULL,
  enabled       TINYINT NOT NULL DEFAULT 1
);
```

## Bộ test được thêm trong lần hoàn thiện này

- `MemoryQuotaServiceTest`
  - flow reserve / duplicate / confirm / release
  - timeout reservation
  - release confirmed theo config
  - concurrent reserve không vượt limit
- `RedisQuotaServiceTest`
  - kiểm tra đúng hành vi với Redis thật qua Testcontainers
- `StaticQuotaPolicyProviderTest`
  - policy tĩnh + fallback default hold
- `JdbcQuotaPolicyProviderTest`
  - đọc policy từ DB + enabled-only + default hold
- `CachingQuotaPolicyProviderTest`
  - cache hit + negative cache + TTL expire
- `QuotaReservationFacadeImplTest`
  - facade truyền đúng limit / hold xuống backend
- `QuotaAutoConfigurationTest`
  - kiểm tra auto-config cho memory / fail-fast Redis / fail-fast JDBC

## Ý nghĩa demo

Nếu tích hợp vào ecommerce hoặc booking, bạn có thể demo như sau:
1. Order Service gọi `reserve` để giữ tạm tồn kho.
2. Nếu thanh toán thành công thì gọi `confirm`.
3. Nếu thanh toán lỗi / timeout thì gọi `release`.
4. Với Redis backend, nhiều instance vẫn dùng chung trạng thái quota và tránh oversell tốt hơn.
