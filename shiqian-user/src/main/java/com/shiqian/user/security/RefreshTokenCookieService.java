package com.shiqian.user.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Refresh Token 的唯一浏览器持久化边界。
 *
 * <p>Refresh Token 只允许存在 HttpOnly Cookie 中，不通过 JSON 暴露给前端 JavaScript。</p>
 */
@Component
public class RefreshTokenCookieService {

    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final String domain;
    private final Duration maxAge;

    public RefreshTokenCookieService(
            @Value("${campushub.auth.refresh-cookie.name:campushub_refresh}") String cookieName,
            @Value("${campushub.auth.refresh-cookie.secure:true}") boolean secure,
            @Value("${campushub.auth.refresh-cookie.same-site:Lax}") String sameSite,
            @Value("${campushub.auth.refresh-cookie.domain:}") String domain,
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpirationMs) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = normalizeSameSite(sameSite);
        this.domain = domain;
        this.maxAge = Duration.ofMillis(Math.max(0L, refreshTokenExpirationMs));
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void write(HttpServletResponse response, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new IllegalArgumentException("refreshToken 不能为空");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, build(refreshToken, maxAge).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    private ResponseCookie build(String value, Duration age) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/user")
                .maxAge(age);
        if (StringUtils.hasText(domain)) {
            builder.domain(domain.trim());
        }
        return builder.build();
    }

    private String normalizeSameSite(String value) {
        if (!StringUtils.hasText(value)) {
            return "Lax";
        }
        return switch (value.trim().toLowerCase()) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
    }
}
