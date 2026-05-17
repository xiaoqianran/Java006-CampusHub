package com.shiqian.gateway.filter;

import com.shiqian.common.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtGlobalAuthFilterTest {

    private JwtUtil jwtUtil;
    private JwtGlobalAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-secret-key-must-be-at-least-256-bits-long");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 7200000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 604800000L);

        filter = new JwtGlobalAuthFilter(jwtUtil);
        ReflectionTestUtils.setField(filter, "whitelist",
                List.of("/api/user/register", "/api/user/login", "/api/user/health", "/actuator"));
    }

    @Test
    void shouldRejectProtectedRequestWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource"));

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void shouldRejectProtectedRequestWithInvalidToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"));

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void shouldPassPublicRequestWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/user/login"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = chainExchange -> {
            captured.set(chainExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertNull(exchange.getResponse().getStatusCode());
        assertNotNull(captured.get());
    }

    @Test
    void shouldPassOptionsRequestWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/resource"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, chainExchange -> {
            captured.set(chainExchange);
            return Mono.empty();
        }).block();

        assertNull(exchange.getResponse().getStatusCode());
        assertNotNull(captured.get());
    }

    @Test
    void shouldAppendUserHeadersWhenTokenValid() {
        String token = jwtUtil.generateAccessToken(1L, "testuser", "USER");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, chainExchange -> {
            captured.set(chainExchange);
            return Mono.empty();
        }).block();

        assertNotNull(captured.get());
        assertEquals("1", captured.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("testuser", captured.get().getRequest().getHeaders().getFirst("X-Username"));
        assertEquals("USER", captured.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void shouldRunBeforeDefaultFilters() {
        assertTrue(filter.getOrder() < 0);
    }
}
