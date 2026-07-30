package com.shiqian.user.service;

import com.shiqian.common.security.AuthoritySnapshot;
import com.shiqian.common.security.TokenKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 共享 Redis 权限快照。用户服务负责回源数据库并写入，其他服务只读取。
 */
@Service
@Slf4j
public class AuthorityCacheService {

    private static final DefaultRedisScript<Long> REPLACE_SET =
            new DefaultRedisScript<>(
                    """
                    redis.call('DEL', KEYS[1])
                    if #ARGV > 1 then
                        for i = 2, #ARGV do
                            redis.call('SADD', KEYS[1], ARGV[i])
                        end
                        redis.call('EXPIRE', KEYS[1], ARGV[1])
                    end
                    return 1
                    """,
                    Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public AuthorityCacheService(
            StringRedisTemplate redisTemplate,
            @Value("${rbac.authority-cache-ttl:24h}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public Optional<AuthoritySnapshot> get(Long userId) {
        try {
            Set<String> authorities = redisTemplate.opsForSet()
                    .members(TokenKey.userAuthorities(userId));
            if (authorities == null || authorities.isEmpty()) {
                return Optional.empty();
            }
            if (authorities.contains(TokenKey.EMPTY_AUTHORITIES_MARKER)) {
                return Optional.of(new AuthoritySnapshot(Set.of(), Set.of()));
            }
            return Optional.of(AuthoritySnapshot.fromGrantedAuthorities(authorities));
        } catch (RuntimeException exception) {
            log.warn("读取权限缓存失败，允许回源数据库: userId={}", userId, exception);
            return Optional.empty();
        }
    }

    public void put(Long userId, AuthoritySnapshot snapshot) {
        Set<String> authorities = snapshot != null
                ? snapshot.asGrantedAuthorities()
                : Set.of();
        if (authorities.isEmpty()) {
            authorities = Set.of(TokenKey.EMPTY_AUTHORITIES_MARKER);
        }
        List<String> args = new ArrayList<>(authorities.size() + 1);
        args.add(String.valueOf(Math.max(1, ttl.toSeconds())));
        args.addAll(authorities);
        try {
            redisTemplate.execute(
                    REPLACE_SET,
                    List.of(TokenKey.userAuthorities(userId)),
                    args.toArray());
        } catch (RuntimeException exception) {
            log.warn("写入权限缓存失败，继续使用数据库结果: userId={}", userId, exception);
        }
    }

    public void evict(Long userId) {
        if (userId != null) {
            try {
                redisTemplate.delete(TokenKey.userAuthorities(userId));
            } catch (RuntimeException exception) {
                log.warn("清理权限缓存失败: userId={}", userId, exception);
            }
        }
    }

    public void evictAll(Iterable<Long> userIds) {
        if (userIds != null) {
            userIds.forEach(this::evict);
        }
    }
}
