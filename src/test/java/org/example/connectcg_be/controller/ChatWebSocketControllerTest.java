package org.example.connectcg_be.controller;

import org.example.connectcg_be.dto.TypingEventDTO;
import org.example.connectcg_be.ratelimit.InMemoryRateLimiter;
import org.example.connectcg_be.realtime.RealtimeEventPublisher;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.WebSocketAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketControllerTest {
    private final RealtimeEventPublisher publisher = mock(RealtimeEventPublisher.class);
    private final WebSocketAuthorizationService authorizationService = mock(WebSocketAuthorizationService.class);
    private final UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
    private final ChatWebSocketController controller = new ChatWebSocketController(
            publisher,
            authorizationService,
            userProfileRepository,
            new InMemoryRateLimiter());

    @Test
    void typingIdentityComesFromAuthenticatedPrincipal() {
        UserPrincipal user = user(7, "john");
        when(authorizationService.canAccessChat(7, "room-key")).thenReturn(true);
        when(userProfileRepository.findByUserId(7)).thenReturn(Optional.empty());
        TypingEventDTO event = new TypingEventDTO("room-key", 999, "spoofed", true);

        controller.handleTyping(event, authentication(user));

        assertEquals(7, event.getUserId());
        assertEquals("john", event.getFullName());
        verify(publisher).sendEphemeralToTopic("/topic/chat/room-key/typing", event);
    }

    @Test
    void nonMemberCannotPublishTypingEvent() {
        UserPrincipal user = user(7, "john");
        when(authorizationService.canAccessChat(7, "room-key")).thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> controller.handleTyping(
                        new TypingEventDTO("room-key", 7, "john", true),
                        authentication(user)));
    }

    @Test
    void excessiveTypingEventsAreDroppedButStopEventPasses() {
        UserPrincipal user = user(7, "john");
        when(authorizationService.canAccessChat(7, "room-key")).thenReturn(true);
        when(userProfileRepository.findByUserId(7)).thenReturn(Optional.empty());

        for (int attempt = 0; attempt < 6; attempt++) {
            controller.handleTyping(
                    new TypingEventDTO("room-key", 7, "john", true),
                    authentication(user));
        }
        TypingEventDTO stopEvent = new TypingEventDTO("room-key", 7, "john", false);
        controller.handleTyping(stopEvent, authentication(user));

        verify(publisher, times(6)).sendEphemeralToTopic(
                org.mockito.ArgumentMatchers.eq("/topic/chat/room-key/typing"),
                org.mockito.ArgumentMatchers.any(TypingEventDTO.class));
        // Verify canAccessChat was only called 6 times (5 typing + 1 stop), NOT 7 times
        verify(authorizationService, times(6)).canAccessChat(7, "room-key");
    }

    @Test
    void excessiveStopEventsAreAlsoDropped() {
        UserPrincipal user = user(8, "alice");
        when(authorizationService.canAccessChat(8, "room-key")).thenReturn(true);
        when(userProfileRepository.findByUserId(8)).thenReturn(Optional.empty());

        for (int attempt = 0; attempt < 10; attempt++) {
            controller.handleTyping(
                    new TypingEventDTO("room-key", 8, "alice", false),
                    authentication(user));
        }

        // Limit is 5, so only 5 stop events should be processed and sent to publisher
        verify(publisher, times(5)).sendEphemeralToTopic(
                org.mockito.ArgumentMatchers.eq("/topic/chat/room-key/typing"),
                org.mockito.ArgumentMatchers.any(TypingEventDTO.class));
        verify(authorizationService, times(5)).canAccessChat(8, "room-key");
    }

    private UserPrincipal user(Integer id, String username) {
        UserPrincipal user = mock(UserPrincipal.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        return user;
    }

    private UsernamePasswordAuthenticationToken authentication(UserPrincipal user) {
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }
}
