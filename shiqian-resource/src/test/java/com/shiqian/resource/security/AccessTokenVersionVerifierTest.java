package com.shiqian.resource.security;

import com.shiqian.common.security.JwtUtil;
import com.shiqian.common.security.TokenKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenVersionVerifierTest {

    private JwtUtil jwtUtil;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private AccessTokenVersionVerifier verifier;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-only-jwt-key-with-at-least-thirty-two-bytes");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 7_200_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 604_800_000L);

        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        verifier = new AccessTokenVersionVerifier(jwtUtil, redisTemplate);
    }

    @Test
    void rejectsRefreshTokenType() {
        String refresh = jwtUtil.generateRefreshToken(1L, "alice", "USER", 1L);
        assertFalse(verifier.isCurrent(jwtUtil.parseToken(refresh)));
    }

    @Test
    void acceptsMatchingAccessTokenVersion() {
        String access = jwtUtil.generateAccessToken(9L, "bob", "USER", 3L);
        when(redisTemplate.hasKey(TokenKey.accessBlacklist(jwtUtil.getJti(access)))).thenReturn(false);
        when(valueOps.get(TokenKey.userVersion(9L))).thenReturn("3");
        assertTrue(verifier.isCurrent(jwtUtil.parseToken(access)));
    }

    @Test
    void rejectsBlacklistedAccessToken() {
        String access = jwtUtil.generateAccessToken(9L, "bob", "USER", 3L);
        when(redisTemplate.hasKey(TokenKey.accessBlacklist(jwtUtil.getJti(access)))).thenReturn(true);
        assertFalse(verifier.isCurrent(jwtUtil.parseToken(access)));
    }

    @Test
    void rejectsMismatchedTokenVersion() {
        String access = jwtUtil.generateAccessToken(9L, "bob", "USER", 3L);
        when(redisTemplate.hasKey(TokenKey.accessBlacklist(jwtUtil.getJti(access)))).thenReturn(false);
        when(valueOps.get(TokenKey.userVersion(9L))).thenReturn("4");
        assertFalse(verifier.isCurrent(jwtUtil.parseToken(access)));
    }
}
