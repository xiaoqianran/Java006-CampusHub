package com.shiqian.gateway.filter;

import com.shiqian.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtGlobalAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-Username";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final JwtUtil jwtUtil;

    @Value("${gateway.auth.whitelist:}")
    private List<String> whitelist;

    private final List<String> DEFAULT_WHITELIST = List.of(
            "/api/user/register",
            "/api/user/login",
            "/api/user/refresh",
            "/api/user/health",
            "/api/resource",
            "/api/category",
            "/actuator"
    );

    public JwtGlobalAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isPublicRequest(exchange)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange);
        if (!StringUtils.hasText(token) || !jwtUtil.validateToken(token)) {
            return unauthorized(exchange);
        }

        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            return unauthorized(exchange);
        }

        ServerWebExchange authenticatedExchange = exchange.mutate()
                .request(builder -> builder
                        .header(USER_ID_HEADER, String.valueOf(claims.get("userId", Long.class)))
                        .header(USERNAME_HEADER, claims.get("username", String.class))
                        .header(USER_ROLE_HEADER, claims.get("role", String.class)))
                .build();
        return chain.filter(authenticatedExchange);
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublicRequest(ServerWebExchange exchange) {
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return true;
        }
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        if (HttpMethod.GET.equals(method)
                && (path.startsWith("/api/resource") || path.startsWith("/api/category"))) {
            return true;
        }
        if (HttpMethod.POST.equals(method) && path.matches("/api/resource/\\d+/download")) {
            return true;
        }
        List<String> effective = (whitelist != null && !whitelist.isEmpty()) ? whitelist : DEFAULT_WHITELIST;
        return effective.stream().anyMatch(path::startsWith);
    }

    private String extractToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        byte[] bytes = "{\"code\":401,\"message\":\"未登录或 token 已过期\",\"data\":null}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
