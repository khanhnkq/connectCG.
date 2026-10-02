package org.example.connectcg_be.service;

import org.example.connectcg_be.entity.Notification;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.NotificationRepository;
import org.example.connectcg_be.repository.UserAvatarRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.realtime.RealtimeEventPublisher;
import org.example.connectcg_be.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserAvatarRepository userAvatarRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User owner;
    private User otherUser;
    private Notification notification;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1);
        owner.setUsername("owner");

        otherUser = new User();
        otherUser.setId(2);
        otherUser.setUsername("attacker");

        notification = new Notification();
        notification.setId(100);
        notification.setUser(owner);
        notification.setIsRead(false);
    }

    @Test
    void markAsRead_WhenOwnerCalls_ShouldSucceed() {
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        notificationService.markAsRead(100, 1);

        assertTrue(notification.getIsRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_WhenNonOwnerCalls_ShouldThrowForbidden() {
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                notificationService.markAsRead(100, 2)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_WhenNotFound_ShouldThrowNotFound() {
        when(notificationRepository.findById(999)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                notificationService.markAsRead(999, 1)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteNotification_WhenOwnerCalls_ShouldSucceed() {
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        notificationService.deleteNotification(100, 1);

        verify(notificationRepository).delete(notification);
    }

    @Test
    void deleteNotification_WhenNonOwnerCalls_ShouldThrowForbidden() {
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                notificationService.deleteNotification(100, 2)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(notificationRepository, never()).delete(any());
    }

    @Test
    void sendNotification_fanOutUsesIsolatedDtoInstances() {
        org.example.connectcg_be.dto.TungNotificationDTO sharedDto = new org.example.connectcg_be.dto.TungNotificationDTO();
        sharedDto.setContent("Broadcast message");
        sharedDto.setType("SYSTEM");

        User user1 = new User();
        user1.setId(10);
        user1.setUsername("user1");

        User user2 = new User();
        user2.setId(20);
        user2.setUsername("user2");

        Notification n1 = new Notification();
        n1.setId(101);
        Notification n2 = new Notification();
        n2.setId(102);

        when(notificationRepository.save(any(Notification.class)))
                .thenReturn(n1)
                .thenReturn(n2);

        notificationService.sendNotification(sharedDto, user1);
        notificationService.sendNotification(sharedDto, user2);

        org.mockito.ArgumentCaptor<org.example.connectcg_be.dto.TungNotificationDTO> captor =
                org.mockito.ArgumentCaptor.forClass(org.example.connectcg_be.dto.TungNotificationDTO.class);
        verify(realtimeEventPublisher, times(2)).sendToUser(anyString(), eq("/queue/notifications"), captor.capture());

        java.util.List<org.example.connectcg_be.dto.TungNotificationDTO> sentDtos = captor.getAllValues();
        assertEquals(2, sentDtos.size());
        assertEquals(101, sentDtos.get(0).getId());
        assertEquals(102, sentDtos.get(1).getId());
        assertNotSame(sentDtos.get(0), sentDtos.get(1));
    }
}
