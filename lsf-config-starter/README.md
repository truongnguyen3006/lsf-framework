# lsf-config-starter

> Starter chuẩn hóa cách service LSF nạp cấu hình từ local folder hoặc Spring Config Server.

Module này giải quyết phần bootstrap cấu hình sớm trong vòng đời Spring Boot. Thay vì mỗi service tự viết lại `spring.config.import`, `lsf-config-starter` cung cấp một convention chung qua namespace `lsf.config`.

## Dùng khi nào?

- Service cần đọc cấu hình từ file/folder local như `./config/`.
- Service cần kết nối Spring Config Server.
- Dự án muốn thống nhất cách bật/tắt config import giữa local, dev và demo.
- Team muốn tránh việc mỗi service tự xử lý config import theo cách khác nhau.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-config-starter</artifactId>
</dependency>
```

## Cấu hình mẫu

Nạp cấu hình từ file hoặc folder local:

```yaml
lsf:
  config:
    enabled: true
    mode: FILE
    import-location: ./config/
    optional: true
```

Nạp theo config tree, phù hợp khi mount config/secret dạng thư mục:

```yaml
lsf:
  config:
    enabled: true
    mode: CONFIGTREE
    import-location: /etc/config/
    optional: true
```

Nạp từ Config Server:

```yaml
lsf:
  config:
    enabled: true
    mode: CONFIG_SERVER
    config-server:
      uri: http://localhost:8888
      fail-fast: false
      label: main
```

## Thuộc tính chính

| Property | Ý nghĩa | Mặc định |
|---|---|---|
| `lsf.config.enabled` | Bật cơ chế config import của LSF | `false` |
| `lsf.config.mode` | Chế độ nạp cấu hình: `NONE`, `FILE`, `CONFIGTREE`, `CONFIG_SERVER` | `NONE` |
| `lsf.config.import-location` | Vị trí folder/file config local | `./config/` |
| `lsf.config.optional` | Cho phép app chạy nếu config ngoài chưa có | `true` |
| `lsf.config.config-server.uri` | URL Spring Config Server | `http://localhost:8888` |
| `lsf.config.config-server.fail-fast` | Fail ngay khi Config Server không sẵn sàng | `false` |

## Cấu trúc module

```text
lsf-config-starter/
├─ LsfConfigProperties.java
├─ LsfConfigAutoConfiguration.java
├─ LsfConfigBootstrapEnvironmentPostProcessor.java
└─ LsfConfigImportSupport.java
```

## Lưu ý

- Module này không triển khai Config Server, chỉ chuẩn hóa cách service consume cấu hình.
- `enabled=false` là mặc định để tránh làm thay đổi hành vi service khi chỉ thêm dependency.
- Với production, nên quản lý secret bằng secret manager hoặc biến môi trường thay vì commit trực tiếp vào file config.
