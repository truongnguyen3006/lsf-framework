package com.myorg.lsf.quota.impl.memory;

import com.myorg.lsf.quota.api.*;
import com.myorg.lsf.quota.config.LsfQuotaProperties;
import com.myorg.lsf.quota.obs.QuotaMetrics;
import lombok.RequiredArgsConstructor;
import com.myorg.lsf.quota.api.QuotaQueryFacade;
import com.myorg.lsf.quota.api.QuotaSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class MemoryQuotaService implements QuotaService, QuotaQueryFacade {
    private final LsfQuotaProperties props;
    private final QuotaMetrics metrics;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new HashMap<>();
    //Vì class này dùng chung một biến Map<String, Bucket> buckets cho mọi luồng (thread) trong Spring Boot
    //synchronized biến hàm thành Khối nguyên tử (Atomic):
    // Chỉ có 1 luồng được chạy qua hàm này tại một thời điểm.
    @Override
    public synchronized QuotaResult reserve(QuotaRequest req){
        long now = clock.millis();
        //lấy ra buket chứa tt tồn kho đang được req(quotaKey), nếu chưa có -> tạo cái mới
        Bucket b = buckets.computeIfAbsent(req.quotaKey(), k -> new Bucket());
        b.purgeExpired(now);

        if(b.confirmed.containsKey(req.requestId())){
            if(metrics != null) metrics.incReserveDuplicate();
            return QuotaResult.builder()
                    .decision(QuotaDecision.DUPLICATE)
                    .state(QuotaState.CONFIRMED)
                    //Trả về số lượng hàng hóa hiện tại đang bị khóa/sử dụng trong kho
                    //=> giúp service khác khi gọi nó biết số lượng hiện tại của kho, hoặc hiển thị
                    .used(b.used)
                    .limit(req.limit())
                    // trả về 0 như "hết hạn" giao dịch không có quyền giữ kho
                    .holdUntilEpochMs(0)
                    .build();
        }
        Reservation existing = b.reserved.get(req.requestId());
        if(existing !=  null) {
            if(metrics != null) metrics.incReserveDuplicate();
            return QuotaResult.builder()
                    .decision(QuotaDecision.DUPLICATE)
                    .state(QuotaState.RESERVED)
                    .used(b.used)
                    // limit do Client(service khác có thể là OrderService gửi)
                    .limit(req.limit())
                    .holdUntilEpochMs(existing.expiresAtMs)
                    .build();
        }
        int amount = Math.max(1, req.amount());
        int limit = req.limit();
        if(b.used + amount  > limit){
            if(metrics != null) metrics.incReserveRejected();
            return QuotaResult.builder()
                    .decision(QuotaDecision.REJECTED)
                    .state(null)
                    .used(b.used)
                    .limit(limit)
                    .holdUntilEpochMs(0)
                    .build();
        }

        Duration hold = (req.hold() != null) ? req.hold() : Duration.ofSeconds(props.getDefaultHoldSeconds());
        long exp = now + Math.max(1, hold.toMillis());
        b.used += amount;
        b.reserved.put(req.requestId(), new Reservation(amount, exp));
        if (metrics != null) metrics.incReserveAccepted();
        return QuotaResult.builder()
                .decision(QuotaDecision.ACCEPTED)
                .state(QuotaState.RESERVED)
                .used(b.used)
                .limit(limit)
                .holdUntilEpochMs(exp)
                .build();
    }

    @Override
    public synchronized QuotaResult confirm(String quotaKey, String requestId) {
        long now = clock.millis();
        Bucket b = buckets.computeIfAbsent(quotaKey, k -> new Bucket());
        b.purgeExpired(now);

        if (b.confirmed.containsKey(requestId)) {
            if (metrics != null) metrics.incConfirmOk();
            return QuotaResult.builder()
                    .decision(QuotaDecision.DUPLICATE)
                    .state(QuotaState.CONFIRMED)
                    .used(b.used)
                    .limit(0)
                    .holdUntilEpochMs(0)
                    .build();
        }
        // Hàm remove(requestId) sẽ lôi cái vé giữ chỗ từ trong danh sách chờ (reserved) ra ngoài
        Reservation r = b.reserved.remove(requestId);
        if (r == null) {
            if (metrics != null) metrics.incConfirmNotFound();
            return QuotaResult.builder()
                    .decision(QuotaDecision.NOT_FOUND)
                    .state(null)
                    .used(b.used)
                    .limit(0)
                    .holdUntilEpochMs(0)
                    .build();
        }

        b.confirmed.put(requestId, r.amount);
        if (metrics != null) metrics.incConfirmOk();
        return QuotaResult.builder()
                .decision(QuotaDecision.ACCEPTED)
                .state(QuotaState.CONFIRMED)
                .used(b.used)
                .limit(0)
                .holdUntilEpochMs(0)
                .build();
    }

    @Override
    public synchronized QuotaResult release(String quotaKey, String requestId) {
        long now = clock.millis();
        Bucket b = buckets.computeIfAbsent(quotaKey, k -> new Bucket());
        b.purgeExpired(now);

        //Xả kho đang giữ chỗ (Release Pending)
        Reservation r = b.reserved.remove(requestId);
        if (r != null) {
            b.used -= r.amount;
            if (metrics != null) metrics.incReleaseOk();
            return QuotaResult.builder()
                    .decision(QuotaDecision.ACCEPTED)
                    .state(null)
                    .used(b.used)
                    .limit(0)
                    .holdUntilEpochMs(0)
                    .build();
        }

        //Nhả hàng đã chốt đơn (Release Confirmed)
        Integer confirmedAmt = b.confirmed.get(requestId);
        if (confirmedAmt != null && props.isAllowReleaseConfirmed()) {
            b.confirmed.remove(requestId);
            b.used -= confirmedAmt;
            if (metrics != null) metrics.incReleaseOk();
            return QuotaResult.builder()
                    .decision(QuotaDecision.ACCEPTED)
                    .state(null).used(b.used)
                    .limit(0)
                    .holdUntilEpochMs(0)
                    .build();
        }

        if (metrics != null) metrics.incReleaseNotFound();
        return QuotaResult.builder()
                .decision(QuotaDecision.NOT_FOUND)
                .state(null).used(b.used)
                .limit(0)
                .holdUntilEpochMs(0)
                .build();
    }

    @Override
    public synchronized QuotaSnapshot getSnapshot(String quotaKey) {
        long now = clock.millis();
        Bucket b = buckets.computeIfAbsent(quotaKey, k -> new Bucket());
        b.purgeExpired(now);

        return QuotaSnapshot.builder()
                .quotaKey(quotaKey)
                .used(b.used)
                .reservedCount(b.reserved.size())
                .confirmedCount(b.confirmed.size())
                .refreshedAtEpochMs(now)
                .build();
    }

    private static class Reservation {
        final int amount;
        final long expiresAtMs;
        Reservation(int amount, long expiresAtMs) { this.amount = amount; this.expiresAtMs = expiresAtMs; }
    }

    private static class Bucket {
        //tổng của tất cả những sp đang bị giữ chỗ + những sp đã bán thành công
        int used = 0;
        final Map<String, Reservation> reserved = new HashMap<>();
        final Map<String, Integer> confirmed = new HashMap<>();

        void purgeExpired(long now) {
            var it = reserved.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (e.getValue().expiresAtMs <= now) {
                    used -= e.getValue().amount;
                    it.remove();
                }
            }
        }
    }
}
