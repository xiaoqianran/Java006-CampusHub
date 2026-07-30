package com.shiqian.gateway.security;

import com.shiqian.common.security.JwtUtil;
import com.shiqian.common.security.TokenKey;
import com.shiqian.common.security.TokenType;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactiveTokenVersionVerifier {

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;

    public ReactiveTokenVersionVerifier(
            JwtUtil jwtUtil,
            ReactiveStringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isCurrent(Claims claims) {
        if (claims == null || !TokenType.ACCESS.name().equals(claims.get("tokenType", String.class))) {
            return Mono.just(false);
        }
        Long userId = jwtUtil.getLongClaim(claims, "userId");
        Long tokenVersion = jwtUtil.getLongClaim(claims, "tokenVersion");
        String jti = claims.getId();
        if (userId == null || tokenVersion == null || jti == null || jti.isBlank()) {
            return Mono.just(false);
        }
        return redisTemplate.hasKey(TokenKey.accessBlacklist(jti))
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return Mono.just(false);
                    }
                    return redisTemplate.opsForValue()
                            .get(TokenKey.userVersion(userId))
                            .map(String.valueOf(tokenVersion)::equals)
                            .defaultIfEmpty(false);
                });
    }
}
