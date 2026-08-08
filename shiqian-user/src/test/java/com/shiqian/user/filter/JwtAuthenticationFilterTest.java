package com.shiqian.user.filter;

import com.shiqian.common.security.AuthoritySnapshot;
import com.shiqian.common.security.JwtUtil;
import com.shiqian.common.security.LoginUser;
import com.shiqian.user.service.RbacService;
import com.shiqian.user.service.TokenSessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private TokenSessionService tokenSessionService;
    @Mock
    private RbacService rbacService;
    @Mock
    private FilterChain filterChain;

    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-only-jwt-key-with-at-least-thirty-two-bytes");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 7_200_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 604_800_000L);
        filter = new JwtAuthenticationFilter(jwtUtil, tokenSessionService, rbacService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void expiredTokenReturns401WithReasonAndSkipsChain() throws Exception {
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 1L);
        String token = jwtUtil.generateAccessToken(1L, "alice", "USER", 1L);
        Thread.sleep(5L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(bearer(token), response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("token_expired"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void invalidTokenContinuesAsAnonymous() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(bearer("not-a-jwt"), response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void nonCurrentAccessTokenReturns401() throws Exception {
        String token = jwtUtil.generateAccessToken(1L, "alice", "USER", 1L);
        when(tokenSessionService.isCurrentAccessToken(any(Claims.class))).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(bearer(token), response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("token_invalid"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void validAccessTokenSetsSecurityContext() throws Exception {
        String token = jwtUtil.generateAccessToken(9L, "carol", "USER", 3L);
        when(tokenSessionService.isCurrentAccessToken(any(Claims.class))).thenReturn(true);
        when(rbacService.getAuthoritySnapshot(9L)).thenReturn(
                new AuthoritySnapshot(Set.of("USER"), Set.of("user:read")));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(bearer(token), response, filterChain);

        verify(filterChain).doFilter(any(), any());
        assertInstanceOf(LoginUser.class,
                SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        LoginUser user = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(9L, user.getUserId());
        assertEquals("carol", user.getUsername());
    }

    private static MockHttpServletRequest bearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
