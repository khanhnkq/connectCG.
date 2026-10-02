package org.example.connectcg_be.config;

import org.example.connectcg_be.security.AuthCookieService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class HttpPrincipalHandshakeInterceptor implements HandshakeInterceptor {
    public static final String AUTHENTICATION_ATTRIBUTE = "authentication";
    public static final String ACCESS_TOKEN_ATTRIBUTE = "accessToken";

    private final AuthCookieService authCookieService;

    public HttpPrincipalHandshakeInterceptor() {
        this(null);
    }

    public HttpPrincipalHandshakeInterceptor(AuthCookieService authCookieService) {
        this.authCookieService = authCookieService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (request.getPrincipal() instanceof UsernamePasswordAuthenticationToken authentication) {
            attributes.put(AUTHENTICATION_ATTRIBUTE, authentication);
        }
        if (authCookieService != null && request instanceof ServletServerHttpRequest servletRequest) {
            String token = authCookieService.readAccessToken(servletRequest.getServletRequest());
            if (token != null) {
                attributes.put(ACCESS_TOKEN_ATTRIBUTE, token);
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No resources are allocated during the handshake.
    }
}
