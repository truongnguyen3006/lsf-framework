package com.myorg.lsf.quota.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "lsf.quota")
public class LsfQuotaProperties {
    private boolean enabled = true;
    /**
     * auto: nếu có RedisConnectionFactory -> redis, không có -> memory
     * redis: bắt buộc dùng redis
     * memory: dùng in-memory (dev/test)
     */
    private Store store = Store.AUTO;
    private String keyPrefix = "lsf:quota:";
    /** hold mặc định (seconds) nếu request không truyền hold */
    private int defaultHoldSeconds = 30;
    /** TTL “giữ key sống” để tránh key tồn tại mãi mãi (seconds) */
    private int keepAliveSeconds = 24 * 3600;
    /** Cho phép release confirmed (cancel) hay không, trả tiền rồi không cho hoàn */
    private boolean allowReleaseConfirmed = false;
    /** Metrics on/off */
    private boolean metricsEnabled = true;
    public enum Store { AUTO, REDIS, MEMORY }

    private List<PolicyItem> policies = new ArrayList<>();

    @Data
    public static class PolicyItem {
        private String key;          // full quotaKey string, keep ':'
        private int limit;
        private Integer holdSeconds;
    }

    //Policy Provider (DB/Static/Auto) + Cache
    private PolicyProvider provider = new PolicyProvider();
    @Data
    public static class PolicyProvider {
        /**
         * AUTO: ưu tiên JDBC nếu có JdbcTemplate, không có thì STATIC (from lsf.quota.policies list)
         * JDBC: bắt buộc đọc DB
         * STATIC: chỉ đọc list policies trong YAML
         */
        private Mode mode = Mode.AUTO;

        private Jdbc jdbc = new Jdbc();
        private Cache cache = new Cache();

        public enum Mode { AUTO, JDBC, STATIC }

        @Data
        public static class Jdbc {
            /**
             * Table name (supports schema.table). Example: quota_policy or mydb.quota_policy
             */
            private String table = "quota_policy";
            private boolean enabledOnly = true;
        }

        @Data
        public static class Cache {
            /**
             * NONE: no cache
             * MEMORY: in-memory TTL cache
             * REDIS: redis TTL cache
             * MEMORY_REDIS: check memory -> redis -> jdbc/static -> fill both
             */
            private CacheMode mode = CacheMode.MEMORY_REDIS;

            /** TTL seconds for cached policy (both memory and redis) */
            private int ttlSeconds = 30;

            /** Max entries for local cache */
            private int localMaxSize = 10_000;

            /** Redis key prefix for policy cache */
            private String redisPrefix = "lsf:quota:policy:";
        }

        public enum CacheMode { NONE, MEMORY, REDIS, MEMORY_REDIS }
    }
}
