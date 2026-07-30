package com.shiqian.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username, String role) {
        return generateAccessToken(userId, username, role, 0L);
    }

    public String generateAccessToken(Long userId, String username, String role, Long tokenVersion) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .claim("tokenType", TokenType.ACCESS.name())
                .claim("tokenVersion", normalizeTokenVersion(tokenVersion))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(Long userId, String username, String role) {
        return generateRefreshToken(userId, username, role, 0L);
    }

    public String generateRefreshToken(Long userId, String username, String role, Long tokenVersion) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .claim("tokenType", TokenType.REFRESH.name())
                .claim("tokenVersion", normalizeTokenVersion(tokenVersion))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            return null;
        } catch (MalformedJwtException e) {
            log.warn("JWT token malformed: {}", e.getMessage());
            return null;
        } catch (io.jsonwebtoken.security.SecurityException e) {
            log.warn("JWT token signature invalid: {}", e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            log.warn("JWT token invalid argument: {}", e.getMessage());
            return null;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("userId", Long.class);
    }

    public String getUsername(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("username", String.class);
    }

    public String getRole(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("role", String.class);
    }

    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("tokenType", String.class) : null;
    }

    public String getJti(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getId() : null;
    }

    public Long getTokenVersion(String token) {
        Claims claims = parseToken(token);
        return claims != null ? getLongClaim(claims, "tokenVersion") : null;
    }

    public Long getLongClaim(Claims claims, String name) {
        if (claims == null) {
            return null;
        }
        Object value = claims.get(name);
        return value instanceof Number number ? number.longValue() : null;
    }

    public boolean isAccessToken(String token) {
        return TokenType.ACCESS.name().equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return TokenType.REFRESH.name().equals(getTokenType(token));
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public boolean validateToken(String token) {
        return parseToken(token) != null;
    }

    private long normalizeTokenVersion(Long tokenVersion) {
        return tokenVersion != null && tokenVersion >= 0 ? tokenVersion : 0L;
    }
}
