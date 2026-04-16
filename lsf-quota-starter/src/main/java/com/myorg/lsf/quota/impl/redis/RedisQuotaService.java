package com.myorg.lsf.quota.impl.redis;

import com.myorg.lsf.quota.api.*;
import com.myorg.lsf.quota.config.LsfQuotaProperties;
import com.myorg.lsf.quota.obs.QuotaMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.myorg.lsf.quota.api.QuotaQueryFacade;
import com.myorg.lsf.quota.api.QuotaSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
public class RedisQuotaService implements QuotaService, QuotaQueryFacade {
    private final StringRedisTemplate redis;
    private final LsfQuotaProperties props;
    private final QuotaMetrics metrics; // nullable
    private final Clock clock;

    // Return: [code, used, state, holdUntil]
    // code: 1 accepted, 0 rejected, 2 duplicate
    // state: 1 reserved, 2 confirmed, 0 none

    @Override
    public QuotaResult reserve(QuotaRequest req) {
        long now = clock.millis();
        Duration hold = (req.hold() != null) ? req.hold() : Duration.ofSeconds(props.getDefaultHoldSeconds());
        long holdMs = Math.max(1, hold.toMillis());
        long keepAliveMs = Math.max(holdMs * 2, Duration.ofSeconds(props.getKeepAliveSeconds()).toMillis());

        Keys k = keys(req.quotaKey());
        //ckey (Counter Key): Một biến đếm lưu tổng số lượng đã dùng (tương đương biến used).
        //resHash (Reserved Hash): Cấu trúc Hash của Redis lưu danh sách khách đang giữ chỗ (tương đương Map reserved).
        //confHash (Confirmed Hash): Cấu trúc Hash lưu danh sách đã chốt đơn (tương đương Map confirmed).
        //zset (Sorted Set):  Redis dùng Sorted Set để lưu danh sách
        // các vé giữ chỗ sắp xếp theo thời gian hết hạn.Việc "dọn rác" (purgeExpired)
        // trong Lua Script nhờ zset này diễn ra với tốc độ cực kì khủng khiếp.
        @SuppressWarnings("unchecked")
        //Thực thi Lua Script
        List<Long> out = (List<Long>) redis.execute(
                RESERVE_SCRIPT,
                List.of(k.ckey, k.resHash, k.confHash, k.zset),
                String.valueOf(now),
                req.requestId(),
                String.valueOf(Math.max(1, req.amount())),
                String.valueOf(req.limit()),
                String.valueOf(holdMs),
                String.valueOf(keepAliveMs)
        );
        return parseReserveResult(out, req.limit());
    }

    @Override
    public QuotaResult confirm(String quotaKey, String requestId) {
        long now = clock.millis();
        long keepAliveMs = Duration.ofSeconds(props.getKeepAliveSeconds()).toMillis();
        Keys k = keys(quotaKey);

        @SuppressWarnings("unchecked")
        List<Long> out = (List<Long>) redis.execute(
                CONFIRM_SCRIPT,
                List.of(k.ckey, k.resHash, k.confHash, k.zset),
                String.valueOf(now),
                requestId,
                String.valueOf(keepAliveMs)
        );

        long code = out.get(0);
        int used = out.get(1).intValue();

        if (code == 1) {
            if (metrics != null) metrics.incConfirmOk();
            return QuotaResult.builder().decision(QuotaDecision.ACCEPTED).state(QuotaState.CONFIRMED).used(used).limit(0).holdUntilEpochMs(0).build();
        }
        if (code == 2) {
            if (metrics != null) metrics.incConfirmOk();
            return QuotaResult.builder().decision(QuotaDecision.DUPLICATE).state(QuotaState.CONFIRMED).used(used).limit(0).holdUntilEpochMs(0).build();
        }
        if (metrics != null) metrics.incConfirmNotFound();
        return QuotaResult.builder().decision(QuotaDecision.NOT_FOUND).state(null).used(used).limit(0).holdUntilEpochMs(0).build();
    }

    @Override
    public QuotaResult release(String quotaKey, String requestId) {
        long now = clock.millis();
        long keepAliveMs = Duration.ofSeconds(props.getKeepAliveSeconds()).toMillis();
        Keys k = keys(quotaKey);

        @SuppressWarnings("unchecked")
        List<Long> out = (List<Long>) redis.execute(
                RELEASE_SCRIPT,
                List.of(k.ckey, k.resHash, k.confHash, k.zset),
                String.valueOf(now),
                requestId,
                props.isAllowReleaseConfirmed() ? "1" : "0",
                String.valueOf(keepAliveMs)
        );

        long code = out.get(0);
        int used = out.get(1).intValue();

        if (code == 1) {
            if (metrics != null) metrics.incReleaseOk();
            return QuotaResult.builder().decision(QuotaDecision.ACCEPTED).state(null).used(used).limit(0).holdUntilEpochMs(0).build();
        }
        if (metrics != null) metrics.incReleaseNotFound();
        return QuotaResult.builder().decision(QuotaDecision.NOT_FOUND).state(null).used(used).limit(0).holdUntilEpochMs(0).build();
    }

    @Override
    public QuotaSnapshot getSnapshot(String quotaKey) {
        long now = clock.millis();
        long keepAliveMs = Duration.ofSeconds(props.getKeepAliveSeconds()).toMillis();
        Keys k = keys(quotaKey);

        @SuppressWarnings("unchecked")
        List<Long> out = (List<Long>) redis.execute(
                SNAPSHOT_SCRIPT,
                List.of(k.ckey, k.resHash, k.confHash, k.zset),
                String.valueOf(now),
                String.valueOf(keepAliveMs)
        );

        int used = out != null && out.size() > 0 ? out.get(0).intValue() : 0;
        int reservedCount = out != null && out.size() > 1 ? out.get(1).intValue() : 0;
        int confirmedCount = out != null && out.size() > 2 ? out.get(2).intValue() : 0;

        return QuotaSnapshot.builder()
                .quotaKey(quotaKey)
                .used(used)
                .reservedCount(reservedCount)
                .confirmedCount(confirmedCount)
                .refreshedAtEpochMs(now)
                .build();
    }

    private QuotaResult parseReserveResult(List<Long> out, int limit) {
        long code = out.get(0);
        int used = out.get(1).intValue();
        long state = out.get(2);
        long holdUntil = out.get(3);

        if (code == 1) {
            if (metrics != null) metrics.incReserveAccepted();
            return QuotaResult.builder()
                    .decision(QuotaDecision.ACCEPTED)
                    .state(state == 2 ? QuotaState.CONFIRMED : QuotaState.RESERVED)
                    .used(used)
                    .limit(limit)
                    .holdUntilEpochMs(holdUntil)
                    .build();
        }
        if (code == 2) {
            if (metrics != null) metrics.incReserveDuplicate();
            return QuotaResult.builder()
                    .decision(QuotaDecision.DUPLICATE)
                    .state(state == 2 ? QuotaState.CONFIRMED : QuotaState.RESERVED)
                    .used(used)
                    .limit(limit)
                    .holdUntilEpochMs(holdUntil)
                    .build();
        }
        if (metrics != null) metrics.incReserveRejected();
        return QuotaResult.builder()
                .decision(QuotaDecision.REJECTED)
                .state(null)
                .used(used)
                .limit(limit)
                .holdUntilEpochMs(0)
                .build();
    }

    private Keys keys(String quotaKey) {
        String base = props.getKeyPrefix() + quotaKey;
        return new Keys(
                base + ":c",
                base + ":res",
                base + ":conf",
                base + ":z"
        );
    }

    private record Keys(String ckey, String resHash, String confHash, String zset) {}

    // KEYS: ckey, resHash, confHash, zset
    // ARGV: nowMs, requestId, amount, limit, holdMs, keepAliveMs
    private static final String RESERVE_LUA = """
            local ckey = KEYS[1]
            local res  = KEYS[2]
            local conf = KEYS[3]
            local zkey = KEYS[4]
    
            local now  = tonumber(ARGV[1])
            local req  = ARGV[2]
            local amt  = tonumber(ARGV[3])
            local lim  = tonumber(ARGV[4])
            local hold = tonumber(ARGV[5])
            local keep = tonumber(ARGV[6])
    
            -- purge expired reservations
            local expired = redis.call('ZRANGEBYSCORE', zkey, '-inf', now)
            if expired and #expired > 0 then
              for i=1,#expired do
                local rid = expired[i]
                local a = redis.call('HGET', res, rid)
                if a then
                  redis.call('HDEL', res, rid)
                  redis.call('ZREM', zkey, rid)
                  local cur = tonumber(redis.call('GET', ckey) or '0')
                  cur = cur - tonumber(a)
                  if cur < 0 then cur = 0 end
                  redis.call('SET', ckey, cur)
                else
                  redis.call('ZREM', zkey, rid)
                end
              end
            end
    
            -- duplicate?
            local a_res = redis.call('HGET', res, req)
            if a_res then
              local cur = tonumber(redis.call('GET', ckey) or '0')
              local holdUntil = tonumber(redis.call('ZSCORE', zkey, req) or '0')
              redis.call('PEXPIRE', ckey, keep)
              redis.call('PEXPIRE', res, keep)
              redis.call('PEXPIRE', conf, keep)
              redis.call('PEXPIRE', zkey, keep)
              return {2, cur, 1, holdUntil}
            end
            local a_conf = redis.call('HGET', conf, req)
            if a_conf then
              local cur = tonumber(redis.call('GET', ckey) or '0')
              redis.call('PEXPIRE', ckey, keep)
              redis.call('PEXPIRE', res, keep)
              redis.call('PEXPIRE', conf, keep)
              redis.call('PEXPIRE', zkey, keep)
              return {2, cur, 2, 0}
            end
    
            local cur = tonumber(redis.call('GET', ckey) or '0')
            if (cur + amt) > lim then
              redis.call('PEXPIRE', ckey, keep)
              redis.call('PEXPIRE', res, keep)
              redis.call('PEXPIRE', conf, keep)
              redis.call('PEXPIRE', zkey, keep)
              return {0, cur, 0, 0}
            end
    
            local holdUntil = now + hold
            redis.call('HSET', res, req, amt)
            redis.call('ZADD', zkey, holdUntil, req)
            cur = cur + amt
            redis.call('SET', ckey, cur)
    
            redis.call('PEXPIRE', ckey, keep)
            redis.call('PEXPIRE', res, keep)
            redis.call('PEXPIRE', conf, keep)
            redis.call('PEXPIRE', zkey, keep)
    
            return {1, cur, 1, holdUntil}
            """;

    // KEYS: ckey, resHash, confHash, zset
    // ARGV: nowMs, requestId, keepAliveMs
    private static final String CONFIRM_LUA = """
            local ckey = KEYS[1]
            local res  = KEYS[2]
            local conf = KEYS[3]
            local zkey = KEYS[4]

            local now  = tonumber(ARGV[1])
            local req  = ARGV[2]
            local keep = tonumber(ARGV[3])

            -- purge expired
            local expired = redis.call('ZRANGEBYSCORE', zkey, '-inf', now)
            if expired and #expired > 0 then
              for i=1,#expired do
                local rid = expired[i]
                local a = redis.call('HGET', res, rid)
                if a then
                  redis.call('HDEL', res, rid)
                  redis.call('ZREM', zkey, rid)
                  local cur = tonumber(redis.call('GET', ckey) or '0')
                  cur = cur - tonumber(a)
                  if cur < 0 then cur = 0 end
                  redis.call('SET', ckey, cur)
                else
                  redis.call('ZREM', zkey, rid)
                end
              end
            end

            local cur = tonumber(redis.call('GET', ckey) or '0')

            if redis.call('HGET', conf, req) then
              redis.call('PEXPIRE', ckey, keep)
              redis.call('PEXPIRE', res, keep)
              redis.call('PEXPIRE', conf, keep)
              redis.call('PEXPIRE', zkey, keep)
              return {2, cur}
            end

            local a = redis.call('HGET', res, req)
            if not a then
              redis.call('PEXPIRE', ckey, keep)
              redis.call('PEXPIRE', res, keep)
              redis.call('PEXPIRE', conf, keep)
              redis.call('PEXPIRE', zkey, keep)
              return {0, cur}
            end

            redis.call('HDEL', res, req)
            redis.call('ZREM', zkey, req)
            redis.call('HSET', conf, req, a)

            redis.call('PEXPIRE', ckey, keep)
            redis.call('PEXPIRE', res, keep)
            redis.call('PEXPIRE', conf, keep)
            redis.call('PEXPIRE', zkey, keep)

            return {1, cur}
            """;

    // KEYS: ckey, resHash, confHash, zset
    // ARGV: nowMs, requestId, allowReleaseConfirmed(0|1), keepAliveMs
    private static final String RELEASE_LUA = """
            local ckey = KEYS[1]
            local res  = KEYS[2]
            local conf = KEYS[3]
            local zkey = KEYS[4]

            local now  = tonumber(ARGV[1])
            local req  = ARGV[2]
            local allowConf = tonumber(ARGV[3])
            local keep = tonumber(ARGV[4])

            -- purge expired
            local expired = redis.call('ZRANGEBYSCORE', zkey, '-inf', now)
            if expired and #expired > 0 then
              for i=1,#expired do
                local rid = expired[i]
                local a = redis.call('HGET', res, rid)
                if a then
                  redis.call('HDEL', res, rid)
                  redis.call('ZREM', zkey, rid)
                  local cur = tonumber(redis.call('GET', ckey) or '0')
                  cur = cur - tonumber(a)
                  if cur < 0 then cur = 0 end
                  redis.call('SET', ckey, cur)
                else
                  redis.call('ZREM', zkey, rid)
                end
              end
            end

            local cur = tonumber(redis.call('GET', ckey) or '0')

            local a = redis.call('HGET', res, req)
            if a then
              redis.call('HDEL', res, req)
              redis.call('ZREM', zkey, req)
              cur = cur - tonumber(a)
              if cur < 0 then cur = 0 end
              redis.call('SET', ckey, cur)

              redis.call('PEXPIRE', ckey, keep)
              redis.call('PEXPIRE', res, keep)
              redis.call('PEXPIRE', conf, keep)
              redis.call('PEXPIRE', zkey, keep)

              return {1, cur}
            end

            if allowConf == 1 then
              local b = redis.call('HGET', conf, req)
              if b then
                redis.call('HDEL', conf, req)
                cur = cur - tonumber(b)
                if cur < 0 then cur = 0 end
                redis.call('SET', ckey, cur)

                redis.call('PEXPIRE', ckey, keep)
                redis.call('PEXPIRE', res, keep)
                redis.call('PEXPIRE', conf, keep)
                redis.call('PEXPIRE', zkey, keep)

                return {1, cur}
              end
            end

            redis.call('PEXPIRE', ckey, keep)
            redis.call('PEXPIRE', res, keep)
            redis.call('PEXPIRE', conf, keep)
            redis.call('PEXPIRE', zkey, keep)

            return {0, cur}
            """;
    private static final String SNAPSHOT_LUA = """
        local ckey = KEYS[1]
        local res  = KEYS[2]
        local conf = KEYS[3]
        local zkey = KEYS[4]

        local now  = tonumber(ARGV[1])
        local keep = tonumber(ARGV[2])

        -- purge expired reservations
        local expired = redis.call('ZRANGEBYSCORE', zkey, '-inf', now)
        if expired and #expired > 0 then
          for i=1,#expired do
            local rid = expired[i]
            local a = redis.call('HGET', res, rid)
            if a then
              redis.call('HDEL', res, rid)
              redis.call('ZREM', zkey, rid)
              local cur = tonumber(redis.call('GET', ckey) or '0')
              cur = cur - tonumber(a)
              if cur < 0 then cur = 0 end
              redis.call('SET', ckey, cur)
            else
              redis.call('ZREM', zkey, rid)
            end
          end
        end

        local used = tonumber(redis.call('GET', ckey) or '0')
        local reservedCount = tonumber(redis.call('HLEN', res) or '0')
        local confirmedCount = tonumber(redis.call('HLEN', conf) or '0')

        redis.call('PEXPIRE', ckey, keep)
        redis.call('PEXPIRE', res, keep)
        redis.call('PEXPIRE', conf, keep)
        redis.call('PEXPIRE', zkey, keep)

        return {used, reservedCount, confirmedCount}
        """;

    private static final DefaultRedisScript<List> RESERVE_SCRIPT = new DefaultRedisScript<>(RESERVE_LUA, List.class);
    private static final DefaultRedisScript<List> CONFIRM_SCRIPT = new DefaultRedisScript<>(CONFIRM_LUA, List.class);
    private static final DefaultRedisScript<List> RELEASE_SCRIPT = new DefaultRedisScript<>(RELEASE_LUA, List.class);
    private static final DefaultRedisScript<List> SNAPSHOT_SCRIPT = new DefaultRedisScript<>(SNAPSHOT_LUA, List.class);
}
