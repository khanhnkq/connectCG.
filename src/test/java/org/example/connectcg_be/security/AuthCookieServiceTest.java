package org.example.connectcg_be.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCookieServiceTest {
    @Test
    void writesHttpOnlySameSiteCookiesWithSeparatePaths() {
        AuthCookieService service = new AuthCookieService(
                "connect_access",
                "connect_refresh",
                true,
                "Lax",
                900_000,
                Duration.ofDays(30));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeSessionCookies(response, "access-value", "refresh-value");

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertEquals(2, cookies.size());
        assertTrue(cookies.get(0).contains("connect_access=access-value"));
        assertTrue(cookies.get(0).contains("Path=/"));
        assertTrue(cookies.get(1).contains("connect_refresh=refresh-value"));
        assertTrue(cookies.get(1).contains("Path=/api/v1/auth"));
        cookies.forEach(cookie -> {
            assertTrue(cookie.contains("HttpOnly"));
            assertTrue(cookie.contains("Secure"));
            assertTrue(cookie.contains("SameSite=Lax"));
        });
    }
}
