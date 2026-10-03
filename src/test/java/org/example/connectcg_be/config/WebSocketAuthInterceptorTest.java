package org.example.connectcg_be.config;

import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.WebSocketAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthInterceptorTest {
    private final WebSocketAuthorizationService authorizationService = mock(WebSocketAuthorizationService.class);
    private final WebSocketAuthInterceptor interceptor =
            new WebSocketAuthInterceptor(authorizationService);

    @Test
    void connectWithoutValidTokenIsRejected() {
        Message<byte[]> message = message(StompCommand.CONNECT, null, null);

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, mockChannel()));
    }

    @Test
    void unauthorizedSubscriptionIsRejected() {
        UserPrincipal user = mock(UserPrincipal.class);
        when(user.getId()).thenReturn(7);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());
        Message<byte[]> message = message(StompCommand.SUBSCRIBE, authentication, "/topic/private-data");

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, mockChannel()));
    }

    @Test
    void authorizedSubscriptionPasses() {
        UserPrincipal user = mock(UserPrincipal.class);
        when(user.getId()).thenReturn(7);
        when(authorizationService.canSubscribe(7, "/topic/posts")).thenReturn(true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());
        Message<byte[]> message = message(StompCommand.SUBSCRIBE, authentication, "/topic/posts");

        assertDoesNotThrow(() -> interceptor.preSend(message, mockChannel()));
    }

    @Test
    void connectUsesAuthenticationCapturedDuringCookieHandshake() {
        UserPrincipal user = mock(UserPrincipal.class);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(Map.of(
                HttpPrincipalHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE,
                authentication));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());

        assertDoesNotThrow(() -> interceptor.preSend(message, mockChannel()));
    }

    @Test
    void subscribeWithRevokedTokenIsRejected() {
        org.example.connectcg_be.security.AccessTokenRevocationService revocationService =
                mock(org.example.connectcg_be.security.AccessTokenRevocationService.class);
        org.example.connectcg_be.security.JwtTokenProvider tokenProvider =
                mock(org.example.connectcg_be.security.JwtTokenProvider.class);
        WebSocketAuthInterceptor securedInterceptor =
                new WebSocketAuthInterceptor(authorizationService, revocationService, tokenProvider);

        UserPrincipal user = mock(UserPrincipal.class);
        when(user.getId()).thenReturn(7);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication);
        accessor.setDestination("/topic/posts");
        accessor.setSessionAttributes(new java.util.HashMap<>(Map.of(
                HttpPrincipalHandshakeInterceptor.ACCESS_TOKEN_ATTRIBUTE, "revoked-token"
        )));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(tokenProvider.validateToken("revoked-token")).thenReturn(true);
        when(revocationService.isRevoked("revoked-token")).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> securedInterceptor.preSend(message, mockChannel()));
    }

    @Test
    void subscribeWithExpiredTokenIsRejected() {
        org.example.connectcg_be.security.AccessTokenRevocationService revocationService =
                mock(org.example.connectcg_be.security.AccessTokenRevocationService.class);
        org.example.connectcg_be.security.JwtTokenProvider tokenProvider =
                mock(org.example.connectcg_be.security.JwtTokenProvider.class);
        WebSocketAuthInterceptor securedInterceptor =
                new WebSocketAuthInterceptor(authorizationService, revocationService, tokenProvider);

        UserPrincipal user = mock(UserPrincipal.class);
        when(user.getId()).thenReturn(7);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication);
        accessor.setDestination("/topic/posts");
        accessor.setSessionAttributes(new java.util.HashMap<>(Map.of(
                HttpPrincipalHandshakeInterceptor.ACCESS_TOKEN_ATTRIBUTE, "expired-token"
        )));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(tokenProvider.validateToken("expired-token")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> securedInterceptor.preSend(message, mockChannel()));
    }

    @Test
    void connectWithNativeAuthorizationBearerHeaderSucceeds() {
        org.example.connectcg_be.security.AccessTokenRevocationService revocationService =
                mock(org.example.connectcg_be.security.AccessTokenRevocationService.class);
        org.example.connectcg_be.security.JwtTokenProvider tokenProvider =
                mock(org.example.connectcg_be.security.JwtTokenProvider.class);
        org.example.connectcg_be.security.CustomUserDetailsService customUserDetailsService =
                mock(org.example.connectcg_be.security.CustomUserDetailsService.class);
        WebSocketAuthInterceptor securedInterceptor =
                new WebSocketAuthInterceptor(authorizationService, revocationService, tokenProvider, customUserDetailsService);

        UserPrincipal user = new UserPrincipal(42, "john", "john@test.com", "pass", true, false, false, List.of(), 1);
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(revocationService.isRevoked("valid-token")).thenReturn(false);
        when(tokenProvider.getUserIdFromJWT("valid-token")).thenReturn(42);
        when(customUserDetailsService.loadUserById(42)).thenReturn(user);
        when(tokenProvider.getAuthVersion("valid-token")).thenReturn(1);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertDoesNotThrow(() -> securedInterceptor.preSend(message, mockChannel()));
    }

    @Test
    void connectWithNativeAccessTokenHeaderSucceeds() {
        org.example.connectcg_be.security.AccessTokenRevocationService revocationService =
                mock(org.example.connectcg_be.security.AccessTokenRevocationService.class);
        org.example.connectcg_be.security.JwtTokenProvider tokenProvider =
                mock(org.example.connectcg_be.security.JwtTokenProvider.class);
        org.example.connectcg_be.security.CustomUserDetailsService customUserDetailsService =
                mock(org.example.connectcg_be.security.CustomUserDetailsService.class);
        WebSocketAuthInterceptor securedInterceptor =
                new WebSocketAuthInterceptor(authorizationService, revocationService, tokenProvider, customUserDetailsService);

        UserPrincipal user = new UserPrincipal(42, "john", "john@test.com", "pass", true, false, false, List.of(), 1);
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(revocationService.isRevoked("valid-token")).thenReturn(false);
        when(tokenProvider.getUserIdFromJWT("valid-token")).thenReturn(42);
        when(customUserDetailsService.loadUserById(42)).thenReturn(user);
        when(tokenProvider.getAuthVersion("valid-token")).thenReturn(1);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("access_token", "valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertDoesNotThrow(() -> securedInterceptor.preSend(message, mockChannel()));
    }

    @Test
    void connectWithLockedAccountThrowsAccessDeniedException() {
        org.example.connectcg_be.security.AccessTokenRevocationService revocationService =
                mock(org.example.connectcg_be.security.AccessTokenRevocationService.class);
        org.example.connectcg_be.security.JwtTokenProvider tokenProvider =
                mock(org.example.connectcg_be.security.JwtTokenProvider.class);
        org.example.connectcg_be.security.CustomUserDetailsService customUserDetailsService =
                mock(org.example.connectcg_be.security.CustomUserDetailsService.class);
        WebSocketAuthInterceptor securedInterceptor =
                new WebSocketAuthInterceptor(authorizationService, revocationService, tokenProvider, customUserDetailsService);

        UserPrincipal lockedUser = new UserPrincipal(42, "john", "john@test.com", "pass", true, true, false, List.of(), 1);
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(revocationService.isRevoked("valid-token")).thenReturn(false);
        when(tokenProvider.getUserIdFromJWT("valid-token")).thenReturn(42);
        when(customUserDetailsService.loadUserById(42)).thenReturn(lockedUser);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> securedInterceptor.preSend(message, mockChannel()));
        org.junit.jupiter.api.Assertions.assertEquals("Account has been locked", ex.getMessage());
    }

    @Test
    void subscribeWithLockedAccountInDatabaseThrowsAccessDeniedException() {
        org.example.connectcg_be.security.AccessTokenRevocationService revocationService =
                mock(org.example.connectcg_be.security.AccessTokenRevocationService.class);
        org.example.connectcg_be.security.JwtTokenProvider tokenProvider =
                mock(org.example.connectcg_be.security.JwtTokenProvider.class);
        org.example.connectcg_be.security.CustomUserDetailsService customUserDetailsService =
                mock(org.example.connectcg_be.security.CustomUserDetailsService.class);
        WebSocketAuthInterceptor securedInterceptor =
                new WebSocketAuthInterceptor(authorizationService, revocationService, tokenProvider, customUserDetailsService);

        UserPrincipal activePrincipal = new UserPrincipal(42, "john", "john@test.com", "pass", true, false, false, List.of(), 1);
        UserPrincipal lockedPrincipal = new UserPrincipal(42, "john", "john@test.com", "pass", true, true, false, List.of(), 1);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(activePrincipal, null, List.of());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication);
        accessor.setDestination("/topic/posts");
        accessor.setSessionAttributes(new java.util.HashMap<>(Map.of(
                HttpPrincipalHandshakeInterceptor.ACCESS_TOKEN_ATTRIBUTE, "token-123"
        )));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(tokenProvider.validateToken("token-123")).thenReturn(true);
        when(revocationService.isRevoked("token-123")).thenReturn(false);
        when(tokenProvider.getUserIdFromJWT("token-123")).thenReturn(42);
        when(customUserDetailsService.loadUserById(42)).thenReturn(lockedPrincipal);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> securedInterceptor.preSend(message, mockChannel()));
        org.junit.jupiter.api.Assertions.assertEquals("Account has been locked", ex.getMessage());
    }

    @Test
    void subscribeWithMismatchedAuthVersionThrowsAccessDeniedException() {
        org.example.connectcg_be.security.AccessTokenRevocationService revocationService =
                mock(org.example.connectcg_be.security.AccessTokenRevocationService.class);
        org.example.connectcg_be.security.JwtTokenProvider tokenProvider =
                mock(org.example.connectcg_be.security.JwtTokenProvider.class);
        org.example.connectcg_be.security.CustomUserDetailsService customUserDetailsService =
                mock(org.example.connectcg_be.security.CustomUserDetailsService.class);
        WebSocketAuthInterceptor securedInterceptor =
                new WebSocketAuthInterceptor(authorizationService, revocationService, tokenProvider, customUserDetailsService);

        UserPrincipal activePrincipal = new UserPrincipal(42, "john", "john@test.com", "pass", true, false, false, List.of(), 1);
        UserPrincipal updatedPrincipal = new UserPrincipal(42, "john", "john@test.com", "pass", true, false, false, List.of(), 2);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(activePrincipal, null, List.of());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication);
        accessor.setDestination("/topic/posts");
        accessor.setSessionAttributes(new java.util.HashMap<>(Map.of(
                HttpPrincipalHandshakeInterceptor.ACCESS_TOKEN_ATTRIBUTE, "token-123"
        )));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(tokenProvider.validateToken("token-123")).thenReturn(true);
        when(revocationService.isRevoked("token-123")).thenReturn(false);
        when(tokenProvider.getUserIdFromJWT("token-123")).thenReturn(42);
        when(customUserDetailsService.loadUserById(42)).thenReturn(updatedPrincipal);
        when(tokenProvider.getAuthVersion("token-123")).thenReturn(1);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> securedInterceptor.preSend(message, mockChannel()));
        org.junit.jupiter.api.Assertions.assertEquals("Session version expired", ex.getMessage());
    }

    private Message<byte[]> message(StompCommand command, UsernamePasswordAuthenticationToken user, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(user);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private org.springframework.messaging.MessageChannel mockChannel() {
        return mock(org.springframework.messaging.MessageChannel.class);
    }
}
