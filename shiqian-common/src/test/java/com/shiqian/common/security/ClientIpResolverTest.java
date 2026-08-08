package com.shiqian.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpResolverTest {

    @Test
    void nullRequestReturnsUnknown() {
        assertEquals("unknown", ClientIpResolver.resolve(null));
        assertFalse(ClientIpResolver.isDirectLoopback(null));
    }

    @Test
    void untrustedPeerIgnoresForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("X-Real-IP", "8.8.8.8");
        assertEquals("203.0.113.10", ClientIpResolver.resolve(request));
    }

    @Test
    void trustedPeerUsesLastNonProxyHopFromXff() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.2");
        assertEquals("1.2.3.4", ClientIpResolver.resolve(request));
    }

    @Test
    void trustedPeerPrefersXRealIpOverXff() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.20");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.2");
        assertEquals("198.51.100.20", ClientIpResolver.resolve(request));
    }

    @Test
    void trustedPeerWithoutForwardedFallsBackToRemote() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");
        assertEquals("192.168.1.10", ClientIpResolver.resolve(request));
    }

    @Test
    void allProxyHopsFallBackToLastSegment() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        assertEquals("10.0.0.2", ClientIpResolver.resolve(request));
    }

    @Test
    void normalizesIpv4MappedAndBracketedLoopback() {
        MockHttpServletRequest mapped = new MockHttpServletRequest();
        mapped.setRemoteAddr("::ffff:127.0.0.1");
        assertTrue(ClientIpResolver.isDirectLoopback(mapped));
        assertEquals("127.0.0.1", ClientIpResolver.resolve(mapped));

        MockHttpServletRequest bracketed = new MockHttpServletRequest();
        bracketed.setRemoteAddr("[::1]");
        assertTrue(ClientIpResolver.isDirectLoopback(bracketed));
    }

    @Test
    void normalizesIpv4MappedPublicAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::ffff:203.0.113.50");
        assertEquals("203.0.113.50", ClientIpResolver.resolve(request));
    }

    @Test
    void stripsIpv6ZoneId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("fe80::1%eth0");
        // link-local is trusted peer; no forwarded header → remote
        assertEquals("fe80::1", ClientIpResolver.resolve(request));
    }

    @Test
    void privateAndLoopbackCidrsAreTrusted() {
        assertEquals("9.9.9.9", resolveFromTrustedPeer("10.1.2.3", "9.9.9.9"));
        assertEquals("9.9.9.9", resolveFromTrustedPeer("172.16.0.5", "9.9.9.9"));
        assertEquals("9.9.9.9", resolveFromTrustedPeer("172.31.255.1", "9.9.9.9"));
        assertEquals("9.9.9.9", resolveFromTrustedPeer("192.168.100.1", "9.9.9.9"));
        assertEquals("9.9.9.9", resolveFromTrustedPeer("169.254.1.1", "9.9.9.9"));
        // 172.15 / 172.32 are not RFC1918
        MockHttpServletRequest notPrivate = new MockHttpServletRequest();
        notPrivate.setRemoteAddr("172.15.0.1");
        notPrivate.addHeader("X-Forwarded-For", "9.9.9.9");
        assertEquals("172.15.0.1", ClientIpResolver.resolve(notPrivate));
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

    private static String resolveFromTrustedPeer(String peer, String client) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        request.addHeader("X-Forwarded-For", client);
        return ClientIpResolver.resolve(request);
    }
}
