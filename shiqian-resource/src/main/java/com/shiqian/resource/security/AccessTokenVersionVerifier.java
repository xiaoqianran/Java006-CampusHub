package com.shiqian.resource.security;

import com.shiqian.common.security.JwtUtil;
import com.shiqian.common.security.TokenKey;
import com.shiqian.common.security.TokenType;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessTokenVersionVerifier {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    public boolean isCurrent(Claims claims) {
        if (claims == null || !TokenType.ACCESS.name().equals(claims.get("tokenType", String.class))) {
            return false;
        }
        Long userId = jwtUtil.getLongClaim(claims, "userId");
        Long tokenVersion = jwtUtil.getLongClaim(claims, "tokenVersion");
        String jti = claims.getId();
        if (userId == null || tokenVersion == null || jti == null || jti.isBlank()) {
            return false;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(TokenKey.accessBlacklist(jti)))) {
            return false;
        }
        String currentVersion = redisTemplate.opsForValue().get(TokenKey.userVersion(userId));
        return String.valueOf(tokenVersion).equals(currentVersion);
    }
}
