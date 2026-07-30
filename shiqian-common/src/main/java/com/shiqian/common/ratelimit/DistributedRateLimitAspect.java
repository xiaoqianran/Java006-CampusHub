package com.shiqian.common.ratelimit;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Aspect
@Component
@Slf4j
@ConditionalOnClass(StringRedisTemplate.class)
public class DistributedRateLimitAspect {

    private static final DefaultRedisScript<Long> FIXED_WINDOW_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                      redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return current
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Value("${platform.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${platform.rate-limit.fail-open:false}")
    private boolean failOpen;

    public DistributedRateLimitAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(rule)")
    public Object enforce(ProceedingJoinPoint joinPoint, DistributedRateLimit rule) throws Throwable {
        if (!enabled) {
            return joinPoint.proceed();
        }
        String key = "rate-limit:" + rule.name() + ":" + hash(resolveIdentity(rule.keyMode()));
        try {
            Long current = redisTemplate.execute(
                    FIXED_WINDOW_SCRIPT,
                    List.of(key),
                    String.valueOf(rule.windowSeconds() * 1000L));
            if (current != null && current > rule.limit()) {
                throw new BusinessException(429, "请求过于频繁，请稍后再试");
            }
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException error) {
            log.error("Redis 分布式限流不可用: rule={}, failOpen={}", rule.name(), failOpen, error);
            if (!failOpen) {
                throw new BusinessException(503, "限流服务暂时不可用");
            }
        }
        return joinPoint.proceed();
    }

    private String resolveIdentity(RateLimitKeyMode mode) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (mode == RateLimitKeyMode.USER && userId != null) {
            return "user:" + userId;
        }
        if (mode == RateLimitKeyMode.USER_OR_IP && userId != null) {
            return "user:" + userId;
        }
        return "ip:" + clientIp();
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return "unknown";
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)), 0, 16);
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
