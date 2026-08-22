package org.example.connectcg_be.config;

import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.WebSocketAuthorizationService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final WebSocketAuthorizationService authorizationService;

    public WebSocketAuthInterceptor(WebSocketAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            authorizeSend(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        UsernamePasswordAuthenticationToken authentication = resolveUser(accessor);
        if (authentication == null) {
            throw new AccessDeniedException("A valid access token is required");
        }
        accessor.setUser(authentication);
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        UserPrincipal user = requireUser(accessor);
        if (!authorizationService.canSubscribe(user.getId(), accessor.getDestination())) {
            throw new AccessDeniedException("Subscription is not allowed");
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        requireUser(accessor);
        if (!authorizationService.canSend(accessor.getDestination())) {
            throw new AccessDeniedException("Destination is not allowed");
        }
    }

    private UserPrincipal requireUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof UserPrincipal user) {
            return user;
        }
        throw new AccessDeniedException("Authentication is required");
    }

    private UsernamePasswordAuthenticationToken resolveUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof UserPrincipal) {
            return authentication;
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null
                && sessionAttributes.get(HttpPrincipalHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE)
                        instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof UserPrincipal) {
            return authentication;
        }

        return null;
    }
}
