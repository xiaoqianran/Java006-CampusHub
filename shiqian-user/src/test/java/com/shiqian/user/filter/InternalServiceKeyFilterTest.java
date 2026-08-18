package com.shiqian.user.filter;

import com.shiqian.common.user.InternalApiHeaders;
import com.shiqian.user.security.InternalServiceKeyValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InternalServiceKeyFilterTest {

    private final InternalServiceKeyFilter filter = new InternalServiceKeyFilter(
            new InternalServiceKeyValidator("test-internal-key"));

    @Test
    void shouldProtectFutureInternalEndpointsWithoutControllerChanges() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/future-endpoint");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldAllowInternalRequestWithCorrectServiceKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/future-endpoint");
        request.addHeader(InternalApiHeaders.SERVICE_KEY, "test-internal-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldNotAffectPublicApiPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }
}
