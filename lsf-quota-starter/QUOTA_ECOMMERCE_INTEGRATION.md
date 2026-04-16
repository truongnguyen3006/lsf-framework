# Quota -> Ecommerce Integration Playbook

Tài liệu này mô tả cách gắn `lsf-quota-streams-starter` vào một flow ecommerce thật, đặc biệt cho các bài toán:

- flash sale
- inventory hold
- oversell prevention
- order timeout hoặc payment timeout

Đây là playbook ở mức integration pattern cho adopter, không phải tài liệu khẳng định framework đã bao phủ mọi biến thể nghiệp vụ ecommerce.

## 1. Vấn đề cần giải quyết

Trong ecommerce large scale, nhiều người dùng có thể cùng đặt một SKU trong cùng một thời điểm. Nếu chỉ trừ tồn kho sau khi thanh toán thành công, hệ thống rất dễ gặp:

- oversell
- race condition
- timeout kéo dài nhưng slot hàng vẫn bị giữ quá lâu

Quota module giải quyết theo mô hình:

- `reserve`
- `confirm`
- `release`

## 2. Mapping sang nghiệp vụ ecommerce

### Reserve

Thực hiện ngay khi user bấm “Đặt hàng” hoặc khi `Order Service` bắt đầu tạo order.

Ví dụ:

- `quotaKey = flashsale:sku-01`
- `requestId = order-1001`
- `amount = 2`

Ý nghĩa:

- giữ tạm 2 đơn vị hàng cho order này
- chưa trừ cứng vĩnh viễn
- nếu vượt limit thì reject ngay

### Confirm

Thực hiện khi payment thành công hoặc khi order được xác nhận chắc chắn.

Ví dụ:

- `confirm(quotaKey, requestId)`

Ý nghĩa:

- reservation chuyển sang trạng thái confirmed
- hàng đã thật sự được chiếm dụng bởi order

### Release

Thực hiện khi:

- payment fail
- user hủy đơn
- order timeout
- reservation hết hạn và cần giải phóng thủ công hoặc tự động

Ví dụ:

- `release(quotaKey, requestId)`

## 3. Luồng microservice gợi ý

### Option A. Gọi trong `Inventory Service`

1. `Order Service` tạo order ở trạng thái `PENDING`
2. `Order Service` phát event `order.created`
3. `Inventory Service` consume event và gọi `quota.reserve(...)`
4. Nếu accepted -> phát `inventory.reserved`
5. Nếu rejected -> phát `inventory.rejected`
6. `Payment Service` xử lý thanh toán
7. Thành công -> `Inventory Service` gọi `quota.confirm(...)`
8. Thất bại hoặc timeout -> `Inventory Service` gọi `quota.release(...)`

### Option B. Gọi tại `Order Service`

1. Client gọi API tạo order
2. `Order Service` gọi `quota.reserve(...)`
3. Nếu reserve ok -> lưu order + outbox event `order.created`
4. Nếu reserve fail -> trả business error ngay cho frontend
5. Payment thành công thì `confirm`, payment fail thì `release`

## 4. Chọn `quotaKey` như thế nào

Nên thiết kế key đủ rõ để map 1-1 với nguồn tài nguyên bị giới hạn.

Ví dụ tốt:

- `flashsale:sku-01`
- `inventory:warehouse-1:sku-01`
- `campaign:2026-03:sku-01`

Không nên dùng key quá chung như:

- `flashsale`
- `inventory`

vì sẽ khó tách policy và khó quan sát.

## 5. Chọn `requestId` như thế nào

Khuyến nghị dùng business id ổn định, ví dụ:

- `orderId`
- `paymentAttemptId`
- `checkoutSessionId`

Lợi ích:

- idempotent tự nhiên
- retry event không bị reserve trùng
- dễ debug hơn UUID ngẫu nhiên

## 6. Policy gợi ý cho flash sale

Ví dụ static YAML đúng với property names hiện tại:

```yaml
lsf:
  quota:
    provider:
      mode: STATIC
    default-hold-seconds: 120
    policies:
      - key: flashsale:sku-01
        limit: 100
        hold-seconds: 90
```

Hoặc lưu trong DB bảng `quota_policy` để quản trị linh hoạt hơn.

## 7. Vì sao capability này có ích cho luận văn

Quota module cho phép framework thể hiện rõ ba đặc trưng của large-scale microservices:

- kiểm soát contention trên tài nguyên hữu hạn
- chuẩn hóa state transition dùng lại được giữa nhiều service
- tách reusable infrastructure logic khỏi business flow cụ thể của adopter

## 8. Những gì vẫn là future work

- lifecycle tự động dọn reservation hết hạn theo business workflow cụ thể của adopter
- richer dashboards và reporting riêng cho quota state
- benchmark rộng hơn cho nhiều kiểu contention và topology triển khai
