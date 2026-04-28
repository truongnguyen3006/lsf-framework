# LSF Deployment Baseline

> CI/CD, Docker Compose và Helm skeleton để đóng gói/chạy thử service dùng LSF.

Các artifact trong thư mục này chứng minh framework có đường đi từ source code tới container và manifest triển khai cơ bản. Đây là baseline để tham khảo, không phải blueprint production hoàn chỉnh cho mọi tổ chức.

## Artifact chính

| Artifact | Vai trò | Trạng thái |
|---|---|---|
| `.github/workflows/ci.yml` | Maven verify, validate compose/chart, sanity build Docker image | Dùng được cho repo hiện tại |
| `.github/workflows/release-candidate.yml` | Đóng gói JAR, Helm chart và image tarball | Release candidate skeleton |
| `lsf-example/Dockerfile` | Image tham khảo cho demo app | Runnable reference |
| `lsf-service-template/Dockerfile` | Dockerfile mẫu cho adopter | Copy/adapt baseline |
| `docker-compose.yml` | Local/dev stack | Kafka, Schema Registry, MySQL, Redis, Zipkin, apps profile |
| `ops/deployment/helm/lsf-service` | Helm chart generic cho Spring Boot service | Skeleton |

## Validate deployment artifacts

Từ root repo:

```powershell
pwsh ./ops/deployment/validate.ps1
```

Script này kiểm tra:

- root `docker-compose.yml`
- monitoring compose
- `helm lint`
- `helm template`

## Build Docker image thủ công

```bash
docker build -f lsf-example/Dockerfile -t lsf-example:local .
docker build -f lsf-service-template/Dockerfile -t lsf-service-template:local .
```

## Chạy local stack

Chỉ infra nền:

```bash
docker compose up -d kafka schema-registry mysql redis zipkin
```

Infra + apps demo:

```bash
docker compose --profile apps up --build
```

PostgreSQL tham khảo cho outbox runtime:

```bash
docker compose --profile postgres up -d postgres
```

## Helm skeleton

Chart nằm ở:

```text
ops/deployment/helm/lsf-service
```

Chart hỗ trợ:

- `Deployment`
- `Service`
- optional `ServiceAccount`
- optional `Ingress`
- optional `HorizontalPodAutoscaler`
- readiness/liveness/startup probes qua Actuator
- env/envFrom cho config và secrets
- resource requests/limits

Render chart:

```bash
helm template template-service ops/deployment/helm/lsf-service
```

Install/upgrade:

```bash
helm upgrade --install template-service ops/deployment/helm/lsf-service
```

Ví dụ values cho service thật:

```yaml
image:
  repository: ghcr.io/acme/order-service
  tag: 1.0.0

spring:
  profiles: kubernetes,outbox-mysql

env:
  SPRING_APPLICATION_NAME: order-service
  LSF_KAFKA_BOOTSTRAP_SERVERS: kafka.kafka.svc.cluster.local:9092
  LSF_SCHEMA_REGISTRY_URL: http://schema-registry.kafka.svc.cluster.local:8081
  SPRING_DATASOURCE_URL: jdbc:mysql://mysql.database.svc.cluster.local:3306/order_service
  SPRING_DATASOURCE_USERNAME: order_service

envFrom:
  secrets:
    - order-service-secrets
```

## Cách adopter nên dùng

1. Bắt đầu từ `lsf-service-template`.
2. Copy Dockerfile và đổi module path nếu service mới đổi tên.
3. Chọn đúng profile/runtime outbox theo database.
4. Dùng Helm chart như skeleton, sau đó override image, env, secret, ingress và resource.
5. Khóa các admin endpoints như Kafka/outbox admin bằng network/internal auth.

## Giới hạn hiện tại

- Chưa publish Maven artifact hoặc Docker image lên registry thật.
- Chưa provision Kafka topics, database, secret store hoặc cloud load balancer.
- Chưa có GitOps manifests cho Argo CD/Flux.
- Chưa có cloud-specific tuning cho EKS/GKE/AKS.
- Helm chart dành cho service Spring Boot adopter, không dành cho từng starter library.
