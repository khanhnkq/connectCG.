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
