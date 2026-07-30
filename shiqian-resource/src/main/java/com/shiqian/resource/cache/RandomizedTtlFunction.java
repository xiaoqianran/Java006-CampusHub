package com.shiqian.resource.cache;

import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 为缓存过期时间增加随机抖动，空值使用单独的短 TTL。
 */
public final class RandomizedTtlFunction implements RedisCacheWriter.TtlFunction {

    private final Duration baseTtl;
    private final Duration nullTtl;
    private final long jitterMillis;

    public RandomizedTtlFunction(Duration baseTtl, Duration nullTtl, Duration jitter) {
        this.baseTtl = requirePositive(baseTtl, "baseTtl");
        this.nullTtl = requirePositive(nullTtl, "nullTtl");
        this.jitterMillis = Math.max(0L, jitter != null ? jitter.toMillis() : 0L);
    }

    @Override
    public Duration getTimeToLive(Object key, Object value) {
        Duration selectedTtl = value == null ? nullTtl : baseTtl;
        long maxJitter = value == null
                ? Math.min(jitterMillis, Math.max(1_000L, selectedTtl.toMillis() / 4))
                : jitterMillis;
        if (maxJitter == 0L) {
            return selectedTtl;
        }
        return selectedTtl.plusMillis(ThreadLocalRandom.current().nextLong(maxJitter + 1));
    }

    private static Duration requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " 必须大于 0");
        }
        return duration;
    }
}
