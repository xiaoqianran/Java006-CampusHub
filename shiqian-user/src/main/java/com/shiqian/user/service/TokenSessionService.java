package com.shiqian.user.service;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.security.JwtUtil;
import com.shiqian.common.security.TokenKey;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Refresh Token 会话存储。Redis 中只保存令牌摘要，不保存明文令牌。
 */
@Service
@RequiredArgsConstructor
public class TokenSessionService {

    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
            """
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """,
            Long.class);
    private static final DefaultRedisScript<Long> SET_MONOTONIC_VERSION = new DefaultRedisScript<>(
            """
                    local current = redis.call('GET', KEYS[1])
                    if (not current) or tonumber(ARGV[1]) >= tonumber(current) then
                        redis.call('SET', KEYS[1], ARGV[1])
                        return 1
                    end
                    return 0
                    """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    public void storeRefreshToken(String token, Long userId, Long tokenVersion) {
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null || !jwtUtil.isRefreshToken(token) || !StringUtils.hasText(claims.getId())) {
            throw new BusinessException(401, "refreshToken 无效");
        }
        Duration ttl = ttl(claims);
        if (ttl.isZero() || ttl.isNegative()) {
            throw new BusinessException(401, "refreshToken 已过期");
        }

        syncUserVersion(userId, tokenVersion);
        String jti = claims.getId();
        redisTemplate.opsForValue().set(TokenKey.refresh(jti), fingerprint(token), ttl);
        redisTemplate.opsForSet().add(TokenKey.userRefresh(userId), jti);
        redisTemplate.expire(TokenKey.userRefresh(userId), Duration.ofMillis(jwtUtil.getRefreshTokenExpiration()));
    }

    /**
     * 原子消费刷新令牌。成功后旧令牌立即失效，实现刷新令牌轮换。
     */
    public void consumeRefreshToken(String token, Long userId, String jti) {
        if (!StringUtils.hasText(jti)) {
            throw new BusinessException(401, "refreshToken 缺少 jti");
        }
        Long deleted = redisTemplate.execute(
                COMPARE_AND_DELETE,
                List.of(TokenKey.refresh(jti)),
                fingerprint(token));
        redisTemplate.opsForSet().remove(TokenKey.userRefresh(userId), jti);
        if (deleted == null || deleted != 1L) {
            throw new BusinessException(401, "refreshToken 已撤销或已被使用");
        }
    }

    public void revokeAll(Long userId) {
        String userRefreshKey = TokenKey.userRefresh(userId);
        Set<String> jtis = redisTemplate.opsForSet().members(userRefreshKey);
        if (jtis != null && !jtis.isEmpty()) {
            List<String> keys = new ArrayList<>(jtis.size());
            jtis.forEach(jti -> keys.add(TokenKey.refresh(jti)));
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(userRefreshKey);
    }

    /**
     * 将当前 Access Token 加入黑名单，键只保留到令牌原始过期时间。
     */
    public void blacklistAccessToken(String token) {
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null || !jwtUtil.isAccessToken(token) || !StringUtils.hasText(claims.getId())) {
            throw new BusinessException(401, "accessToken 无效");
        }
        Duration ttl = ttl(claims);
        if (!ttl.isZero() && !ttl.isNegative()) {
            redisTemplate.opsForValue().set(
                    TokenKey.accessBlacklist(claims.getId()), "1", ttl);
        }
    }

    public void syncUserVersion(Long userId, Long tokenVersion) {
        Long updated = redisTemplate.execute(
                SET_MONOTONIC_VERSION,
                List.of(TokenKey.userVersion(userId)),
                String.valueOf(normalizeVersion(tokenVersion)));
        if (updated == null || updated != 1L) {
            throw new BusinessException(409, "用户安全状态已变化，请重新登录");
        }
    }

    public boolean isCurrentAccessToken(Claims claims) {
        if (claims == null || !com.shiqian.common.security.TokenType.ACCESS.name()
                .equals(claims.get("tokenType", String.class))) {
            return false;
        }
        Long userId = jwtUtil.getLongClaim(claims, "userId");
        Long tokenVersion = jwtUtil.getLongClaim(claims, "tokenVersion");
        String jti = claims.getId();
        if (userId == null || tokenVersion == null || !StringUtils.hasText(jti)) {
            return false;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(TokenKey.accessBlacklist(jti)))) {
            return false;
        }
        String currentVersion = redisTemplate.opsForValue().get(TokenKey.userVersion(userId));
        return String.valueOf(tokenVersion).equals(currentVersion);
    }

    private Duration ttl(Claims claims) {
        if (claims.getExpiration() == null) {
            return Duration.ZERO;
        }
        return Duration.between(Instant.now(), claims.getExpiration().toInstant());
    }

    private String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("无法计算令牌摘要", e);
        }
    }

    private long normalizeVersion(Long tokenVersion) {
        return tokenVersion != null && tokenVersion >= 0 ? tokenVersion : 0L;
    }
}
