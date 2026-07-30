package com.shiqian.resource.security;

import com.shiqian.common.result.Result;
import com.shiqian.common.security.AuthoritySnapshot;
import com.shiqian.common.security.TokenKey;
import com.shiqian.resource.client.UserPublicProfileClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 从共享 Redis 读取用户实时权限；缓存未命中时安全回源用户服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuthorityProvider {

    private final StringRedisTemplate redisTemplate;
    private final UserPublicProfileClient userClient;

    public AuthoritySnapshot getAuthorities(Long userId) {
        if (userId == null) {
            return emptySnapshot();
        }
        try {
            Set<String> cached = redisTemplate.opsForSet()
                    .members(TokenKey.userAuthorities(userId));
            if (cached != null && !cached.isEmpty()) {
                if (cached.contains(TokenKey.EMPTY_AUTHORITIES_MARKER)) {
                    return emptySnapshot();
                }
                return AuthoritySnapshot.fromGrantedAuthorities(cached);
            }
        } catch (RuntimeException exception) {
            log.warn("读取用户权限缓存失败，回源用户服务: userId={}", userId, exception);
        }

        try {
            Result<AuthoritySnapshot> result = userClient.getAuthorities(userId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                return result.getData();
            }
            log.warn("用户权限回源返回降级结果: userId={}, code={}",
                    userId, result != null ? result.getCode() : null);
        } catch (RuntimeException exception) {
            log.warn("用户权限回源失败: userId={}", userId, exception);
        }
        return emptySnapshot();
    }

    private AuthoritySnapshot emptySnapshot() {
        return new AuthoritySnapshot(Set.of(), Set.of());
    }
}
