package org.example.connectcg_be.controller;

import org.example.connectcg_be.dto.TypingEventDTO;
import lombok.RequiredArgsConstructor;
import org.example.connectcg_be.realtime.RealtimeEventPublisher;
import org.example.connectcg_be.ratelimit.InMemoryRateLimiter;
import org.example.connectcg_be.ratelimit.RateLimitPolicy;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.WebSocketAuthorizationService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final WebSocketAuthorizationService authorizationService;
    private final UserProfileRepository userProfileRepository;
    private final InMemoryRateLimiter inMemoryRateLimiter;

    @MessageMapping("/chat/typing")
    public void handleTyping(TypingEventDTO event, Principal principal) {
        UserPrincipal currentUser = resolveUser(principal);
        if (event == null || event.getFirebaseRoomKey() == null || currentUser == null
                || !authorizationService.canAccessChat(currentUser.getId(), event.getFirebaseRoomKey())) {
            throw new AccessDeniedException("You are not a member of this chat");
        }
        event.setUserId(currentUser.getId());
        event.setFullName(userProfileRepository.findByUserId(currentUser.getId())
                .map(profile -> profile.getFullName())
                .orElse(currentUser.getUsername()));
        if (event.isTyping() && !inMemoryRateLimiter
                .acquire("websocket-typing:" + currentUser.getId(), RateLimitPolicy.WEBSOCKET_TYPING)
                .allowed()) {
            return;
        }
        realtimeEventPublisher.sendEphemeralToTopic(
                "/topic/chat/" + event.getFirebaseRoomKey() + "/typing",
                event);
    }

    private UserPrincipal resolveUser(Principal principal) {
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof UserPrincipal user) {
            return user;
        }
        return null;
    }
}
