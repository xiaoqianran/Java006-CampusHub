package com.shiqian.user.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrowserAuthOriginFilterTest {

    private final BrowserAuthOriginFilter filter = new BrowserAuthOriginFilter(
            "https://frontend.example.com,http://localhost:5173");

    @Test
    void shouldRejectUntrustedBrowserOriginOnRefresh() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/user/refresh");
        request.addHeader("Origin", "https://evil.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldAllowTrustedOriginAndNonBrowserClient() throws Exception {
        MockHttpServletRequest trusted = new MockHttpServletRequest("POST", "/api/user/login");
        trusted.addHeader("Origin", "https://frontend.example.com");
        MockHttpServletResponse trustedResponse = new MockHttpServletResponse();
        filter.doFilter(trusted, trustedResponse, new MockFilterChain());
        assertEquals(200, trustedResponse.getStatus());

        MockHttpServletRequest noOrigin = new MockHttpServletRequest("POST", "/api/user/refresh");
        MockHttpServletResponse noOriginResponse = new MockHttpServletResponse();
        filter.doFilter(noOrigin, noOriginResponse, new MockFilterChain());
        assertEquals(200, noOriginResponse.getStatus());
    }
}
