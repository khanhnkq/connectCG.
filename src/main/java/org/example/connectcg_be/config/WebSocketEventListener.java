package org.example.connectcg_be.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.dto.UserStatusDTO;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.OnlineUserService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessageSendingOperations messagingTemplate;
    private final OnlineUserService onlineUserService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        Principal user = event.getUser();
        UserPrincipal userPrincipal = resolveUser(user);
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (userPrincipal == null || sessionId == null) {
            return;
        }

        Integer userId = userPrincipal.getId();
        if (onlineUserService.connect(userId, sessionId)) {
            log.info("User online: {}", userId);
            messagingTemplate.convertAndSend(
                    "/topic/public/status",
                    new UserStatusDTO(userId, "ONLINE"));
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();
        
        UserPrincipal userPrincipal = resolveUser(user);
        String sessionId = headerAccessor.getSessionId();
        if (userPrincipal == null || sessionId == null) {
            return;
        }

        Integer userId = userPrincipal.getId();
        if (onlineUserService.disconnect(userId, sessionId)) {
            log.info("User offline: {}", userId);
            messagingTemplate.convertAndSend(
                    "/topic/public/status",
                    new UserStatusDTO(userId, "OFFLINE"));
        }
    }

    private UserPrincipal resolveUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof UserPrincipal user) {
            return user;
        }
        return null;
    }
}
