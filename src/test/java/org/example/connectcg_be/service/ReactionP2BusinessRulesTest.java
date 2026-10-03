package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.ReactionEventDTO;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.Reaction;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.ReactionRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.service.impl.ReactionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactionP2BusinessRulesTest {
    @Mock private ReactionRepository reactionRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private NotificationService notificationService;
    @Mock private org.example.connectcg_be.repository.NotificationRepository notificationRepository;
    @Mock private PostAccessPolicy postAccessPolicy;
    @Mock private PostRealtimeService postRealtimeService;

    @InjectMocks private ReactionServiceImpl reactionService;

    @Test
    void invalidReactionTypeIsRejectedBeforeDatabaseMutation() {
        assertThrows(IllegalArgumentException.class, () -> reactionService.reactToPost(10, 2, "INVALID"));
        verify(reactionRepository, never()).save(org.mockito.ArgumentMatchers.any(Reaction.class));
    }

    @Test
    void realtimeEventAndStoredCounterUseTheAuthoritativeReactionCount() {
        Post post = new Post();
        post.setId(10);
        User author = new User();
        author.setId(2);
        post.setAuthor(author);

        when(postRepository.findById(10)).thenReturn(Optional.of(post));
        when(reactionRepository.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(2)).thenReturn(author);
        when(postRepository.findReactCountById(10)).thenReturn(4);

        reactionService.reactToPost(10, 2, "LOVE");

        verify(postRepository).adjustReactCount(10, 1);
        ArgumentCaptor<ReactionEventDTO> eventCaptor = ArgumentCaptor.forClass(ReactionEventDTO.class);
        verify(postRealtimeService).publishReactionEvent(org.mockito.ArgumentMatchers.eq(post), eventCaptor.capture());
        assertEquals(4, eventCaptor.getValue().getNewReactCount());
    }

    @Test
    void reactToPost_whenRecentNotificationExists_reusesNotificationInstance() {
        Post post = new Post();
        post.setId(10);
        User author = new User();
        author.setId(1);
        post.setAuthor(author);

        User reactor = new User();
        reactor.setId(2);
        reactor.setUsername("reactor");

        org.example.connectcg_be.entity.Notification existingNotification = new org.example.connectcg_be.entity.Notification();
        existingNotification.setId(999);
        existingNotification.setUser(author);
        existingNotification.setActor(reactor);
        existingNotification.setType("POST_REACTION");
        existingNotification.setCreatedAt(java.time.Instant.now().minus(java.time.Duration.ofMinutes(1)));

        when(postRepository.findById(10)).thenReturn(Optional.of(post));
        when(reactionRepository.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(2)).thenReturn(reactor);
        when(notificationRepository.findFirstByUserIdAndActorIdAndTypeAndTargetTypeAndTargetIdOrderByCreatedAtDesc(
                1, 2, "POST_REACTION", "POST", 10))
                .thenReturn(Optional.of(existingNotification));
        when(userProfileRepository.findByUserId(2)).thenReturn(Optional.empty());

        reactionService.reactToPost(10, 2, "LIKE");

        ArgumentCaptor<org.example.connectcg_be.entity.Notification> notiCaptor =
                ArgumentCaptor.forClass(org.example.connectcg_be.entity.Notification.class);
        verify(notificationService).sendNotification(notiCaptor.capture());

        org.example.connectcg_be.entity.Notification passedNoti = notiCaptor.getValue();
        assertEquals(999, passedNoti.getId());
    }
}
