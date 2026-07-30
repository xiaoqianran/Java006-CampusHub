package com.shiqian.common.ratelimit;

import com.shiqian.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedRateLimitAspectTest {

    private StringRedisTemplate redisTemplate;
    private DistributedRateLimitAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private DistributedRateLimit rule;

    @BeforeEach
    void setUp() throws Exception {
        redisTemplate = mock(StringRedisTemplate.class);
        joinPoint = mock(ProceedingJoinPoint.class);
        aspect = new DistributedRateLimitAspect(redisTemplate);
        ReflectionTestUtils.setField(aspect, "enabled", true);
        ReflectionTestUtils.setField(aspect, "failOpen", false);
        Method method = Fixture.class.getDeclaredMethod("limited");
        rule = method.getAnnotation(DistributedRateLimit.class);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldProceedWithinLimit() throws Throwable {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class))).thenReturn(2L);
        when(joinPoint.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.enforce(joinPoint, rule));
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
                () -> aspect.enforce(joinPoint, rule));

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
                () -> aspect.enforce(joinPoint, rule));

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

        assertEquals("fallback", aspect.enforce(joinPoint, rule));
    }

    private static final class Fixture {
        @DistributedRateLimit(
                name = "fixture",
                limit = 3,
                windowSeconds = 60,
                keyMode = RateLimitKeyMode.IP)
        void limited() {
        }
    }
}
