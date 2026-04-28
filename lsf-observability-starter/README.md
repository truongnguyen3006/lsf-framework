# lsf-observability-starter

> Starter bổ sung metrics, MDC và observation hooks cho event dispatch trong LSF.

Module này làm cho luồng async dễ quan sát hơn bằng cách bọc `LsfDispatcher`, ghi tags theo topic/eventType/outcome và đưa metadata quan trọng vào MDC/log context.

## Dùng khi nào?

- Service consume event và cần biết handler nào thành công/thất bại.
- Muốn Prometheus/Grafana có metrics về event dispatch.
- Muốn log có correlation/event metadata để debug luồng bất đồng bộ.
- Muốn dùng Micrometer Observation quanh dispatcher.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-observability-starter</artifactId>
</dependency>
```

Nên dùng kèm `lsf-eventing-starter`.

## Cấu hình mẫu

```yaml
lsf:
  observability:
    enabled: true
    mdc-enabled: true
    metrics-enabled: true
    tracing-enabled: true
    tag-topic: true
    tag-event-type: true
    tag-outcome: true
    tag-event-id: false
```

## Cung cấp gì?

| Class | Vai trò |
|---|---|
| `ObservingLsfDispatcher` | Bọc dispatcher để đo duration/outcome |
| `LsfMetrics` | Helper ghi metrics |
| `LsfMdc` | Đưa event metadata vào MDC |
| `LsfContext` | Context helper cho event handling |
| `LsfObservabilityProperties` | Binding namespace `lsf.observability` |

## Metrics/metadata thường dùng

- event dispatch duration
- success/failure/duplicate/ignored outcome
- topic
- event type
- producer
- correlation id
- causation id

## Lưu ý

- Không nên bật `tag-event-id=true` ở tải lớn vì event id có cardinality cao.
- Module này cần Micrometer/Actuator nếu muốn expose metrics ra Prometheus.
- Observability không thay thế DLQ/admin tooling; nó giúp phát hiện và truy vết nhanh hơn.
