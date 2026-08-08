package com.shiqian.common.ratelimit;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.security.LoginUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedRateLimitAspectTest {

    private StringRedisTemplate redisTemplate;
    private DistributedRateLimitAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private DistributedRateLimit ipRule;
    private DistributedRateLimit userRule;
    private DistributedRateLimit userOrIpRule;

    @BeforeEach
    void setUp() throws Exception {
        redisTemplate = mock(StringRedisTemplate.class);
        joinPoint = mock(ProceedingJoinPoint.class);
        aspect = new DistributedRateLimitAspect(redisTemplate);
        ReflectionTestUtils.setField(aspect, "enabled", true);
        ReflectionTestUtils.setField(aspect, "failOpen", false);
        ipRule = Fixture.class.getDeclaredMethod("limited").getAnnotation(DistributedRateLimit.class);
        userRule = Fixture.class.getDeclaredMethod("userLimited").getAnnotation(DistributedRateLimit.class);
        userOrIpRule = Fixture.class.getDeclaredMethod("userOrIpLimited")
                .getAnnotation(DistributedRateLimit.class);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldProceedWithinLimit() throws Throwable {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class))).thenReturn(2L);
        when(joinPoint.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.enforce(joinPoint, ipRule));
        verify(joinPoint).proceed();
    }

    @Test
    void shouldRejectWhenLimitExceeded() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class))).thenReturn(4L);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> aspect.enforce(joinPoint, ipRule));

        assertEquals(429, error.getCode());
    }

    @Test
    void shouldFailClosedWhenRedisUnavailable() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class))).thenThrow(new IllegalStateException("redis down"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> aspect.enforce(joinPoint, ipRule));

        assertEquals(503, error.getCode());
    }

    @Test
    void shouldFailOpenOnlyWhenExplicitlyConfigured() throws Throwable {
        ReflectionTestUtils.setField(aspect, "failOpen", true);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class))).thenThrow(new IllegalStateException("redis down"));
        when(joinPoint.proceed()).thenReturn("fallback");

        assertEquals("fallback", aspect.enforce(joinPoint, ipRule));
    }

    @Test
    void untrustedPeerIgnoresSpoofedForwardedIpInRateKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String withSpoof = captureRateKey(ipRule);

        MockHttpServletRequest clean = new MockHttpServletRequest();
        clean.setRemoteAddr("203.0.113.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(clean));
        String withoutSpoof = captureRateKey(ipRule);

        assertEquals(withSpoof, withoutSpoof);
        assertTrue(withSpoof.startsWith("rate-limit:fixture:"));
    }

    @Test
    void trustedPeerUsesClientIpFromForwardedHeaderInRateKey() {
        MockHttpServletRequest spoofedPublic = new MockHttpServletRequest();
        spoofedPublic.setRemoteAddr("203.0.113.10");
        spoofedPublic.addHeader("X-Forwarded-For", "1.2.3.4");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(spoofedPublic));
        String untrustedKey = captureRateKey(ipRule);

        MockHttpServletRequest viaProxy = new MockHttpServletRequest();
        viaProxy.setRemoteAddr("10.0.0.2");
        viaProxy.addHeader("X-Forwarded-For", "1.2.3.4");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(viaProxy));
        String trustedKey = captureRateKey(ipRule);

        assertNotEquals(untrustedKey, trustedKey);
    }

    @Test
    void userModeFallsBackToIpWhenAnonymous() {
        String ipOnly = captureRateKey(ipRule);
        String userFallback = captureRateKey(userRule);
        assertTrue(ipOnly.startsWith("rate-limit:fixture:"));
        assertTrue(userFallback.startsWith("rate-limit:fixture-user:"));
    }

    @Test
    void userModeUsesAuthenticatedUserId() {
        authenticate(42L);
        String asUser = captureRateKey(userRule);

        MockHttpServletRequest otherIp = new MockHttpServletRequest();
        otherIp.setRemoteAddr("198.51.100.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(otherIp));
        String sameUserOtherIp = captureRateKey(userRule);

        assertEquals(asUser, sameUserOtherIp);
    }

    @Test
    void userOrIpUsesUserWhenPresentOtherwiseIp() {
        authenticate(7L);
        String asUser = captureRateKey(userOrIpRule);
        SecurityContextHolder.clearContext();
        String asIp = captureRateKey(userOrIpRule);
        assertNotEquals(asUser, asIp);
    }

    @SuppressWarnings("unchecked")
    private String captureRateKey(DistributedRateLimit rule) {
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                keysCaptor.capture(),
                any(Object[].class))).thenReturn(1L);
        try {
            aspect.enforce(joinPoint, rule);
        } catch (Throwable error) {
            throw new AssertionError(error);
        }
        return String.valueOf(keysCaptor.getValue().get(0));
    }

    private static void authenticate(Long userId) {
        LoginUser user = new LoginUser(userId, "u" + userId, "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static final class Fixture {
        @DistributedRateLimit(
                name = "fixture",
                limit = 3,
                windowSeconds = 60,
                keyMode = RateLimitKeyMode.IP)
        void limited() {
        }

        @DistributedRateLimit(
                name = "fixture-user",
                limit = 3,
                windowSeconds = 60,
                keyMode = RateLimitKeyMode.USER)
        void userLimited() {
        }

        @DistributedRateLimit(
                name = "fixture-user-or-ip",
                limit = 3,
                windowSeconds = 60,
                keyMode = RateLimitKeyMode.USER_OR_IP)
        void userOrIpLimited() {
        }
    }
}
