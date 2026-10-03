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
    private final org.example.connectcg_be.security.AccessTokenRevocationService revocationService;
    private final org.example.connectcg_be.security.JwtTokenProvider tokenProvider;
    private final org.example.connectcg_be.security.CustomUserDetailsService customUserDetailsService;

    public WebSocketAuthInterceptor(WebSocketAuthorizationService authorizationService) {
        this(authorizationService, null, null, null);
    }

    public WebSocketAuthInterceptor(
            WebSocketAuthorizationService authorizationService,
            org.example.connectcg_be.security.AccessTokenRevocationService revocationService,
            org.example.connectcg_be.security.JwtTokenProvider tokenProvider) {
        this(authorizationService, revocationService, tokenProvider, null);
    }

    public WebSocketAuthInterceptor(
            WebSocketAuthorizationService authorizationService,
            org.example.connectcg_be.security.AccessTokenRevocationService revocationService,
            org.example.connectcg_be.security.JwtTokenProvider tokenProvider,
            org.example.connectcg_be.security.CustomUserDetailsService customUserDetailsService) {
        this.authorizationService = authorizationService;
        this.revocationService = revocationService;
        this.tokenProvider = tokenProvider;
        this.customUserDetailsService = customUserDetailsService;
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
            validateSessionNotRevoked(accessor);
            authorizeSubscribe(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            validateSessionNotRevoked(accessor);
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

        String token = extractToken(accessor);
        if (token != null && accessor.getSessionAttributes() != null) {
            accessor.getSessionAttributes().put(HttpPrincipalHandshakeInterceptor.ACCESS_TOKEN_ATTRIBUTE, token);
        }

        validateSessionNotRevoked(accessor);
    }

    private void validateSessionNotRevoked(StompHeaderAccessor accessor) {
        if (revocationService == null || tokenProvider == null) {
            return;
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        String token = sessionAttributes != null
                ? (String) sessionAttributes.get(HttpPrincipalHandshakeInterceptor.ACCESS_TOKEN_ATTRIBUTE)
                : null;

        if (token != null) {
            if (!tokenProvider.validateToken(token)) {
                throw new AccessDeniedException("Access token expired or invalid");
            }
            if (revocationService.isRevoked(token)) {
                throw new AccessDeniedException("Session has been revoked");
            }
            if (customUserDetailsService != null) {
                try {
                    Integer userId = tokenProvider.getUserIdFromJWT(token);
                    org.springframework.security.core.userdetails.UserDetails userDetails =
                            customUserDetailsService.loadUserById(userId);
                    if (userDetails instanceof UserPrincipal principal) {
                        if (!principal.isAccountNonLocked()) {
                            throw new AccessDeniedException("Account has been locked");
                        }
                        if (!principal.isEnabled()) {
                            throw new AccessDeniedException("Account has been disabled");
                        }
                        if (tokenProvider.getAuthVersion(token) != principal.getAuthVersion()) {
                            throw new AccessDeniedException("Session version expired");
                        }
                    }
                } catch (AccessDeniedException ade) {
                    throw ade;
                } catch (Exception e) {
                    throw new AccessDeniedException("Failed to validate user account status");
                }
            }
        } else if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof UserPrincipal principal) {
            if (customUserDetailsService != null) {
                try {
                    org.springframework.security.core.userdetails.UserDetails userDetails =
                            customUserDetailsService.loadUserById(principal.getId());
                    if (userDetails instanceof UserPrincipal livePrincipal) {
                        if (!livePrincipal.isAccountNonLocked()) {
                            throw new AccessDeniedException("Account has been locked");
                        }
                        if (!livePrincipal.isEnabled()) {
                            throw new AccessDeniedException("Account has been disabled");
                        }
                    }
                } catch (AccessDeniedException ade) {
                    throw ade;
                } catch (Exception e) {
                    throw new AccessDeniedException("Failed to validate user account status");
                }
            }
        }
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

        // Fallback: Check STOMP native headers (Authorization: Bearer <token> or access_token)
        String token = extractToken(accessor);
        if (token != null && tokenProvider != null && customUserDetailsService != null) {
            if (!tokenProvider.validateToken(token)) {
                throw new AccessDeniedException("Access token expired or invalid");
            }
            if (revocationService != null && revocationService.isRevoked(token)) {
                throw new AccessDeniedException("Session has been revoked");
            }
            try {
                Integer userId = tokenProvider.getUserIdFromJWT(token);
                org.springframework.security.core.userdetails.UserDetails userDetails =
                        customUserDetailsService.loadUserById(userId);
                if (userDetails instanceof UserPrincipal userPrincipal) {
                    if (!userPrincipal.isAccountNonLocked()) {
                        throw new AccessDeniedException("Account has been locked");
                    }
                    if (!userPrincipal.isEnabled()) {
                        throw new AccessDeniedException("Account has been disabled");
                    }
                    if (tokenProvider.getAuthVersion(token) != userPrincipal.getAuthVersion()) {
                        throw new AccessDeniedException("Session version expired");
                    }
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    if (sessionAttributes == null) {
                        sessionAttributes = new java.util.HashMap<>();
                        accessor.setSessionAttributes(sessionAttributes);
                    }
                    sessionAttributes.put(HttpPrincipalHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE, auth);
                    sessionAttributes.put(HttpPrincipalHandshakeInterceptor.ACCESS_TOKEN_ATTRIBUTE, token);
                    return auth;
                }
            } catch (AccessDeniedException ade) {
                throw ade;
            } catch (Exception e) {
                throw new AccessDeniedException("Failed to validate user account status");
            }
        }

        return null;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        String accessTokenHeader = accessor.getFirstNativeHeader("access_token");
        if (accessTokenHeader != null && !accessTokenHeader.isBlank()) {
            return accessTokenHeader.trim();
        }
        return null;
    }
}
