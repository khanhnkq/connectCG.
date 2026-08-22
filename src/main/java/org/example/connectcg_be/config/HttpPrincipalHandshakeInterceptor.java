package org.example.connectcg_be.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class HttpPrincipalHandshakeInterceptor implements HandshakeInterceptor {
    static final String AUTHENTICATION_ATTRIBUTE = "authentication";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (request.getPrincipal() instanceof UsernamePasswordAuthenticationToken authentication) {
            attributes.put(AUTHENTICATION_ATTRIBUTE, authentication);
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
