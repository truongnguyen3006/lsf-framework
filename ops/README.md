# LSF Operations

> Baseline vận hành cho LSF: metrics, tracing, Grafana/Prometheus, Zipkin, DLQ admin và outbox admin.

Thư mục `ops/` không phải production ops suite hoàn chỉnh. Nó cung cấp các artifact tham khảo để demo framework và làm nền cho adopter tự mở rộng theo môi trường thật.

## Thành phần

```text
ops/
├─ monitoring/
│  ├─ docker-compose.monitoring.yml
│  ├─ prometheus/
│  ├─ grafana/
│  └─ alertmanager/
└─ deployment/
   ├─ README.md
   ├─ validate.ps1
   └─ helm/lsf-service/
```

## Monitoring stack

| Thành phần | URL mặc định | Vai trò |
|---|---|---|
| Prometheus | `http://localhost:9090` | Scrape metrics từ Actuator |
| Grafana | `http://localhost:3000` | Dashboard framework operations |
| Alertmanager | `http://localhost:9093` | Alert baseline |
| Zipkin | `http://localhost:9411` | Trace viewer |

## Chạy local

Khởi động infra chính ở root repo nếu cần:

```bash
docker compose up -d kafka schema-registry mysql redis zipkin
```

Khởi động monitoring stack:

```bash
docker compose -f ops/monitoring/docker-compose.monitoring.yml up -d
```

Service cần quan sát nên expose:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0
```

## Starter thường đi kèm

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-observability-starter</artifactId>
</dependency>

<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-kafka-admin-starter</artifactId>
</dependency>

<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-admin-starter</artifactId>
</dependency>
```

## Playbook ngắn

| Tình huống | Việc nên làm |
|---|---|
| DLQ tăng | Inspect qua `/lsf/kafka/dlq/records`, kiểm tra header lỗi và replay nếu an toàn |
| Outbox pending tăng | Kiểm tra Kafka, scheduler, datasource và dùng outbox admin để requeue |
| Event handler fail tăng | Đối chiếu `eventId`, `correlationId`, log MDC và Zipkin trace |
| Metrics không xuất hiện | Kiểm tra Actuator exposure, Prometheus scrape config và network |

## Lưu ý

- Không expose `/lsf/kafka/**` hoặc `/lsf/outbox/**` ra public internet.
- Dashboard/alert chỉ là baseline framework-level, chưa bao phủ business SLA.
- Local/dev có thể trace sampling `1.0`; môi trường tải lớn nên giảm sampling.
- Tài liệu deployment chi tiết nằm ở [deployment/README.md](deployment/README.md).
