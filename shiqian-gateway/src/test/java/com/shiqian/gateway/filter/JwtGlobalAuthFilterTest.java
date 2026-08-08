package com.shiqian.gateway.filter;

import com.shiqian.common.security.JwtUtil;
import com.shiqian.gateway.security.ReactiveTokenVersionVerifier;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtGlobalAuthFilterTest {

    private JwtUtil jwtUtil;
    private JwtGlobalAuthFilter filter;
    private ReactiveTokenVersionVerifier tokenVersionVerifier;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-secret-key-must-be-at-least-256-bits-long");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 7200000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 604800000L);

        tokenVersionVerifier = mock(ReactiveTokenVersionVerifier.class);
        when(tokenVersionVerifier.isCurrent(any())).thenReturn(Mono.just(true));
        filter = new JwtGlobalAuthFilter(jwtUtil, tokenVersionVerifier);
        ReflectionTestUtils.setField(filter, "whitelist",
                List.of("/api/user/register", "/api/user/login", "/api/user/refresh", "/api/user/health", "/actuator/health"));
    }

    @Test
    void shouldRejectProtectedRequestWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/me"));

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void shouldRejectProtectedRequestWithInvalidToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/me")
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
    void shouldPassActuatorHealthWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, chainExchange -> {
            captured.set(chainExchange);
            return Mono.empty();
        }).block();

        assertNull(exchange.getResponse().getStatusCode());
        assertNotNull(captured.get());
    }

    @Test
    void shouldRejectActuatorPrometheusWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus"));
        filter.filter(exchange, chain -> Mono.empty()).block();
        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void shouldPassPublicTagQueryWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/tag"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, chainExchange -> {
            captured.set(chainExchange);
            return Mono.empty();
        }).block();

        assertNull(exchange.getResponse().getStatusCode());
        assertNotNull(captured.get());
    }

    @Test
    void shouldPassAnonymousResourceViewWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/resource/42/view"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, chainExchange -> {
            captured.set(chainExchange);
            return Mono.empty();
        }).block();

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
                MockServerHttpRequest.get("/api/user/me")
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
    void shouldRejectRefreshTokenOnProtectedEndpoint() {
        String token = jwtUtil.generateRefreshToken(1L, "testuser", "ADMIN");
        when(tokenVersionVerifier.isCurrent(any())).thenReturn(Mono.just(false));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void shouldStripSpoofedIdentityHeaders() {
        String token = jwtUtil.generateAccessToken(1L, "testuser", "USER");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Id", "999")
                        .header("X-Username", "attacker")
                        .header("X-User-Role", "ADMIN"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, chainExchange -> {
            captured.set(chainExchange);
            return Mono.empty();
        }).block();

        assertEquals("1", captured.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("testuser", captured.get().getRequest().getHeaders().getFirst("X-Username"));
        assertEquals("USER", captured.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void shouldRequireAuthForMineFavoritesAndRecycle() {
        MockServerWebExchange mine = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource/mine"));
        filter.filter(mine, chain -> Mono.empty()).block();
        assertEquals(401, mine.getResponse().getStatusCode().value());

        MockServerWebExchange favorites = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource/favorites"));
        filter.filter(favorites, chain -> Mono.empty()).block();
        assertEquals(401, favorites.getResponse().getStatusCode().value());

        MockServerWebExchange recycle = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource/recycle-bin"));
        filter.filter(recycle, chain -> Mono.empty()).block();
        assertEquals(401, recycle.getResponse().getStatusCode().value());
    }

    @Test
    void shouldPassPublicResourceDetailWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource/42"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        filter.filter(exchange, chainExchange -> {
            captured.set(chainExchange);
            return Mono.empty();
        }).block();
        assertNull(exchange.getResponse().getStatusCode());
        assertNotNull(captured.get());
    }

    @Test
    void shouldInjectIdentityOnPublicResourceWhenTokenPresent() {
        String token = jwtUtil.generateAccessToken(1L, "testuser", "USER");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource/42")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        filter.filter(exchange, chainExchange -> {
            captured.set(chainExchange);
            return Mono.empty();
        }).block();
        assertEquals("1", captured.get().getRequest().getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void shouldRunBeforeDefaultFilters() {
        assertTrue(filter.getOrder() < 0);
    }
}
