package org.example.connectcg_be.service;

import org.example.connectcg_be.entity.FriendRequest;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.*;
import org.example.connectcg_be.service.impl.FriendRequestServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendRequestConcurrencyTest {

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private FriendRepository friendRepository;

    @Mock
    private FriendSuggestionRepository friendSuggestionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FriendRequestServiceImpl friendRequestService;

    @Test
    @DisplayName("Concurrent accept fails when another transaction already changed status from PENDING")
    void concurrentAcceptFailsWhenStatusNoLongerPending() {
        FriendRequest request = new FriendRequest();
        request.setId(10);
        request.setStatus("PENDING");
        request.setReceiver(createUser(2));
        request.setSender(createUser(1));

        when(friendRequestRepository.findByIdAndReceiverId(10, 2)).thenReturn(Optional.of(request));
        // Return 0 updated rows -> simulates another thread already accepted or rejected it
        when(friendRequestRepository.updateStatusIfPending(eq(10), eq(2), eq("ACCEPTED"), any()))
                .thenReturn(0);

        assertThrows(RuntimeException.class, () -> friendRequestService.acceptFriendRequest(10, 2));

        // Side effects (creating friend record, sending notification) must NEVER execute
        verify(friendRepository, never()).save(any());
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    @DisplayName("Send friend request fails if reverse pending request already exists")
    void sendFriendRequestFailsIfReversePendingExists() {
        when(friendRepository.existsByUserIdAndFriendId(1, 2)).thenReturn(false);
        when(friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(1, 2, "PENDING")).thenReturn(false);
        // Reverse direction: 2 -> 1 is already pending
        when(friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(2, 1, "PENDING")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> friendRequestService.sendFriendRequest(1, 2));
        verify(friendRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Send friend request handles database unique constraint violation gracefully")
    void sendFriendRequestHandlesDataIntegrityViolation() {
        User sender = createUser(1);
        User receiver = createUser(2);

        when(friendRepository.existsByUserIdAndFriendId(1, 2)).thenReturn(false);
        when(friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(1, 2, "PENDING")).thenReturn(false);
        when(friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(2, 1, "PENDING")).thenReturn(false);
        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2)).thenReturn(Optional.of(receiver));

        // Simulate DB-level unique constraint collision from race condition
        when(friendRequestRepository.save(any(FriendRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        assertThrows(RuntimeException.class, () -> friendRequestService.sendFriendRequest(1, 2));
    }

    private User createUser(Integer id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        return user;
    }
}
