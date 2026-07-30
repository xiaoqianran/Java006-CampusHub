package com.shiqian.user.service;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TokenSessionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;

    private JwtUtil jwtUtil;
    private TokenSessionService tokenSessionService;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-only-jwt-key-with-at-least-thirty-two-bytes");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 7_200_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 604_800_000L);
        tokenSessionService = new TokenSessionService(redisTemplate, jwtUtil);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStoreOnlyRefreshTokenFingerprint() {
        String token = jwtUtil.generateRefreshToken(9L, "alice", "USER", 2L);
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        tokenSessionService.storeRefreshToken(token, 9L, 2L);

        ArgumentCaptor<String> storedValue = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                any(String.class), storedValue.capture(), any(Duration.class));
        assertNotEquals(token, storedValue.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshTokenCanOnlyBeConsumedOnce() {
        String token = jwtUtil.generateRefreshToken(9L, "alice", "USER", 2L);
        String jti = jwtUtil.getJti(token);
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L, 0L);

        tokenSessionService.consumeRefreshToken(token, 9L, jti);

        assertThrows(BusinessException.class,
                () -> tokenSessionService.consumeRefreshToken(token, 9L, jti));
    }

    @Test
    @SuppressWarnings("unchecked")
    void staleLoginCannotLowerCurrentSecurityVersion() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        assertThrows(BusinessException.class,
                () -> tokenSessionService.syncUserVersion(9L, 1L));
    }

    @Test
    void blacklistedAccessTokenMustBeRejected() {
        String token = jwtUtil.generateAccessToken(9L, "alice", "USER", 2L);
        when(redisTemplate.hasKey(any(String.class))).thenReturn(true);

        assertFalse(tokenSessionService.isCurrentAccessToken(jwtUtil.parseToken(token)));
    }

    @Test
    void blacklistEntryMustExpireWithAccessToken() {
        String token = jwtUtil.generateAccessToken(9L, "alice", "USER", 2L);

        tokenSessionService.blacklistAccessToken(token);

        verify(valueOperations).set(any(String.class), any(String.class), any(Duration.class));
    }
}
