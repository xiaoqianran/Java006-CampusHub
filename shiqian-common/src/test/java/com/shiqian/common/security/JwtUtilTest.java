package com.shiqian.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-only-jwt-key-with-at-least-thirty-two-bytes");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 7_200_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 604_800_000L);
    }

    @Test
    void shouldDistinguishAccessAndRefreshTokensAndAssignUniqueJti() {
        String access = jwtUtil.generateAccessToken(7L, "alice", "USER", 3L);
        String refresh = jwtUtil.generateRefreshToken(7L, "alice", "USER", 3L);

        Claims accessClaims = jwtUtil.parseToken(access);
        Claims refreshClaims = jwtUtil.parseToken(refresh);

        assertEquals(TokenType.ACCESS.name(), accessClaims.get("tokenType", String.class));
        assertEquals(TokenType.REFRESH.name(), refreshClaims.get("tokenType", String.class));
        assertEquals(3L, jwtUtil.getLongClaim(accessClaims, "tokenVersion"));
        assertNotNull(accessClaims.getId());
        assertNotNull(refreshClaims.getId());
        assertNotEquals(accessClaims.getId(), refreshClaims.getId());
        assertTrue(jwtUtil.isAccessToken(access));
        assertTrue(jwtUtil.isRefreshToken(refresh));
    }
}
