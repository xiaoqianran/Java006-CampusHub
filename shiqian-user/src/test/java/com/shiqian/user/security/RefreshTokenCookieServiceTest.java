package com.shiqian.user.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenCookieServiceTest {

    @Test
    void shouldWriteHttpOnlySecureCookieWithoutExposingTokenToJavascript() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(
                "campushub_refresh", true, "None", "", 604800000L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.write(response, "refresh-token-value");

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("campushub_refresh=refresh-token-value"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=None"));
        assertTrue(setCookie.contains("Path=/api/user"));
    }

    @Test
    void shouldReadAndClearRefreshCookie() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(
                "campushub_refresh", false, "Lax", "", 604800000L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie("campushub_refresh", "token-123"));

        assertEquals("token-123", service.read(request));

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.clear(response);
        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("campushub_refresh="));
        assertTrue(setCookie.contains("Max-Age=0"));
        assertTrue(setCookie.contains("HttpOnly"));
    }
}
