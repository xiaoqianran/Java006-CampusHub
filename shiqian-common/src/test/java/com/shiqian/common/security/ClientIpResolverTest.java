package com.shiqian.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpResolverTest {

    @Test
    void untrustedPeerIgnoresForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        assertEquals("203.0.113.10", ClientIpResolver.resolve(request));
    }

    @Test
    void trustedPeerUsesLastNonProxyHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.2");
        assertEquals("1.2.3.4", ClientIpResolver.resolve(request));
    }

    @Test
    void directLoopbackDoesNotTrustForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        assertFalse(ClientIpResolver.isDirectLoopback(request));

        MockHttpServletRequest local = new MockHttpServletRequest();
        local.setRemoteAddr("127.0.0.1");
        local.addHeader("X-Forwarded-For", "8.8.8.8");
        assertTrue(ClientIpResolver.isDirectLoopback(local));
    }
}
