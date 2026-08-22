package org.example.connectcg_be.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
public class AuthCookieService {
    private final String accessCookieName;
    private final String refreshCookieName;
    private final boolean secure;
    private final String sameSite;
    private final Duration accessTokenLifetime;
    private final Duration refreshTokenLifetime;

    public AuthCookieService(
            @Value("${app.auth.access-cookie-name:connect_access}") String accessCookieName,
            @Value("${app.auth.refresh-cookie-name:connect_refresh}") String refreshCookieName,
            @Value("${app.auth.cookie-secure:false}") boolean secure,
            @Value("${app.auth.cookie-same-site:Lax}") String sameSite,
            @Value("${app.jwtExpirationInMs:900000}") long accessTokenLifetimeMillis,
            @Value("${app.auth.refresh-token-lifetime:30d}") Duration refreshTokenLifetime) {
        this.accessCookieName = accessCookieName;
        this.refreshCookieName = refreshCookieName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.accessTokenLifetime = Duration.ofMillis(accessTokenLifetimeMillis);
        this.refreshTokenLifetime = refreshTokenLifetime;
    }

    public void writeSessionCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        addCookie(response, accessCookieName, accessToken, "/", accessTokenLifetime);
        addCookie(response, refreshCookieName, refreshToken, "/api/v1/auth", refreshTokenLifetime);
    }

    public void clearSessionCookies(HttpServletResponse response) {
        addCookie(response, accessCookieName, "", "/", Duration.ZERO);
        addCookie(response, refreshCookieName, "", "/api/v1/auth", Duration.ZERO);
    }

    public String readAccessToken(HttpServletRequest request) {
        return readCookie(request, accessCookieName);
    }

    public String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, refreshCookieName);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            String path,
            Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
