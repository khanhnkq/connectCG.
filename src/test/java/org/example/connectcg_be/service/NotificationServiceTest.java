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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
    private org.example.connectcg_be.repository.UserRepository userRepository;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;
    @Mock
    private org.example.connectcg_be.queue.producer.NotificationQueueProducer notificationQueueProducer;

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
    void markAllAsRead_callsRepositoryBulkUpdate() {
        when(notificationRepository.markAllAsReadByUserId(1)).thenReturn(5);

        notificationService.markAllAsRead(1);

        verify(notificationRepository).markAllAsReadByUserId(1);
    }

    @Test
    void markAllAsRead_nullUserId_skipsRepository() {
        notificationService.markAllAsRead(null);

        verify(notificationRepository, never()).markAllAsReadByUserId(any());
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

    @Test
    void sendNotificationBatch_enqueuesTaskWhenProducerSucceeds() {
        org.example.connectcg_be.dto.TungNotificationDTO dto = new org.example.connectcg_be.dto.TungNotificationDTO();
        dto.setContent("Hello all");
        dto.setType("GROUP_ANNOUNCEMENT");
        dto.setTargetType("GROUP");
        dto.setTargetId(10);

        User u1 = new User();
        u1.setId(11);
        User u2 = new User();
        u2.setId(12);

        when(notificationQueueProducer.enqueueFanout(any(), any(), any(), any(), any(), any())).thenReturn(true);

        notificationService.sendNotificationBatch(dto, java.util.List.of(u1, u2), owner);

        verify(notificationQueueProducer).enqueueFanout(
                eq(java.util.List.of(11, 12)),
                eq(owner.getId()),
                eq("Hello all"),
                eq("GROUP_ANNOUNCEMENT"),
                eq("GROUP"),
                eq(10)
        );
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void sendNotificationBatch_fallsBackToSyncWhenQueueFails() {
        org.example.connectcg_be.dto.TungNotificationDTO dto = new org.example.connectcg_be.dto.TungNotificationDTO();
        dto.setContent("Hello fallback");
        dto.setType("NOTICE");
        dto.setTargetType("SYSTEM");
        dto.setTargetId(1);

        User u1 = new User();
        u1.setId(11);
        u1.setUsername("u1");

        when(notificationQueueProducer.enqueueFanout(any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(userRepository.findAllById(any())).thenReturn(java.util.List.of(u1));
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.sendNotificationBatch(dto, java.util.List.of(u1), owner);

        verify(notificationRepository).saveAll(any());
        verify(realtimeEventPublisher).sendToUser(eq("u1"), eq("/queue/notifications"), any());
    }

    @Test
    void processFanoutNotification_partitionsAndSavesAll() {
        org.example.connectcg_be.queue.dto.NotificationFanoutMessage message =
                org.example.connectcg_be.queue.dto.NotificationFanoutMessage.builder()
                        .messageId("msg-1")
                        .recipientUserIds(java.util.List.of(11, 12))
                        .actorId(owner.getId())
                        .content("Worker test")
                        .type("TEST")
                        .targetType("POST")
                        .targetId(22)
                        .createdAt(java.time.Instant.now())
                        .build();

        User u1 = new User();
        u1.setId(11);
        u1.setUsername("u1");
        User u2 = new User();
        u2.setId(12);
        u2.setUsername("u2");

        when(userRepository.findById(owner.getId())).thenReturn(java.util.Optional.of(owner));
        when(userRepository.findAllById(any())).thenReturn(java.util.List.of(u1, u2));
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.processFanoutNotification(message);

        verify(notificationRepository).saveAll(any());
        verify(realtimeEventPublisher, times(2)).sendToUser(anyString(), eq("/queue/notifications"), any());
    }

    @Test
    void getMyNotifications_nullUserId_returnsEmptyList() {
        List<org.example.connectcg_be.dto.TungNotificationDTO> result = notificationService.getMyNotifications(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void getMyNotifications_withPagination_batchesProfilesAndAvatars() {
        User actor1 = new User();
        actor1.setId(201);
        actor1.setUsername("actor_one");

        Notification n1 = new Notification();
        n1.setId(1);
        n1.setUser(owner);
        n1.setActor(actor1);
        n1.setContent("Actor 1 liked your post");
        n1.setType("LIKE");
        n1.setTargetType("POST");
        n1.setTargetId(10);
        n1.setIsRead(false);
        n1.setCreatedAt(java.time.Instant.now());

        Notification n2 = new Notification();
        n2.setId(2);
        n2.setUser(owner);
        n2.setActor(null); // system notification
        n2.setContent("System maintenance");
        n2.setType("SYSTEM");
        n2.setTargetType("SYSTEM");
        n2.setTargetId(0);
        n2.setIsRead(true);
        n2.setCreatedAt(java.time.Instant.now());

        org.springframework.data.domain.Page<Notification> page = new org.springframework.data.domain.PageImpl<>(
                List.of(n1, n2),
                org.springframework.data.domain.PageRequest.of(0, 20),
                2
        );

        when(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(eq(1), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        org.example.connectcg_be.entity.UserProfile profile = new org.example.connectcg_be.entity.UserProfile();
        profile.setUser(actor1);
        profile.setFullName("Nguyen Van A");

        org.example.connectcg_be.entity.UserAvatar avatar = new org.example.connectcg_be.entity.UserAvatar();
        avatar.setUser(actor1);
        org.example.connectcg_be.entity.Media media = new org.example.connectcg_be.entity.Media();
        media.setUrl("https://example.com/avatar.jpg");
        avatar.setMedia(media);

        when(userProfileRepository.findAllByUserIdIn(anyCollection())).thenReturn(List.of(profile));
        when(userAvatarRepository.findCurrentByUserIds(anyCollection())).thenReturn(List.of(avatar));

        List<org.example.connectcg_be.dto.TungNotificationDTO> results = notificationService.getMyNotifications(1, 0, 20);

        assertEquals(2, results.size());

        // Verification of n1 (with actor)
        org.example.connectcg_be.dto.TungNotificationDTO dto1 = results.get(0);
        assertEquals(1, dto1.getId());
        assertEquals("Nguyen Van A", dto1.getActorName());
        assertEquals("https://example.com/avatar.jpg", dto1.getActorAvatar());
        assertFalse(dto1.getIsRead());

        // Verification of n2 (system)
        org.example.connectcg_be.dto.TungNotificationDTO dto2 = results.get(1);
        assertEquals(2, dto2.getId());
        assertEquals("Hệ thống", dto2.getActorName());
        assertEquals("https://cdn-icons-png.flaticon.com/512/149/149071.png", dto2.getActorAvatar());
        assertTrue(dto2.getIsRead());

        // Crucial: verify batch methods called only ONCE (no N+1 storm)
        verify(userProfileRepository, times(1)).findAllByUserIdIn(anyCollection());
        verify(userAvatarRepository, times(1)).findCurrentByUserIds(anyCollection());
        verify(userProfileRepository, never()).findByUserId(anyInt());
        verify(userAvatarRepository, never()).findByUserIdAndIsCurrentTrue(anyInt());
    }
}
