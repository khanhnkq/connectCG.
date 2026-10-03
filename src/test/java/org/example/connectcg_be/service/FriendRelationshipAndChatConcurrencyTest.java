package org.example.connectcg_be.service;

import org.example.connectcg_be.cache.PublicProfileCache;
import org.example.connectcg_be.cache.PublicProfileFragment;
import org.example.connectcg_be.dto.ChatRoomDTO;
import org.example.connectcg_be.dto.UserProfileDTO;
import org.example.connectcg_be.entity.ChatRoom;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.entity.UserProfile;
import org.example.connectcg_be.repository.*;
import org.example.connectcg_be.service.impl.ChatRoomServiceImpl;
import org.example.connectcg_be.service.impl.UserProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendRelationshipAndChatConcurrencyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserAvatarRepository userAvatarRepository;

    @Mock
    private UserCoverRepository userCoverRepository;

    @Mock
    private FriendRepository friendRepository;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private UserHobbyRepository userHobbyRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private PostService postService;

    @Mock
    private PublicProfileCache publicProfileCache;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @InjectMocks
    private UserProfileServiceImpl userProfileService;

    @InjectMocks
    private ChatRoomServiceImpl chatRoomService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("determineRelationship returns WAITING when target user sent a pending friend request")
    void getProfile_shouldReturnWaiting_whenTargetUserSentPendingRequest() {
        Integer targetUserId = 10;
        Integer currentUserId = 20;

        User targetUser = new User();
        targetUser.setId(targetUserId);
        targetUser.setUsername("alice");
        targetUser.setEmail("alice@test.com");
        targetUser.setRole("USER");

        UserProfile profile = new UserProfile();
        profile.setUser(targetUser);
        profile.setFullName("Alice Wonderland");

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(userProfileRepository.findByUserId(targetUserId)).thenReturn(Optional.of(profile));
        when(publicProfileCache.find(targetUserId)).thenReturn(Optional.of(
                new PublicProfileFragment(targetUserId, "Alice Wonderland", "avatar.png", "cover.png")
        ));
        when(userHobbyRepository.findByUserId(targetUserId)).thenReturn(Collections.emptyList());

        // Not friends
        when(friendRepository.existsByUserIdAndFriendId(currentUserId, targetUserId)).thenReturn(false);
        // Current user did NOT send request
        when(friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(currentUserId, targetUserId, "PENDING"))
                .thenReturn(false);
        // Target user SENT request to current user!
        when(friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(targetUserId, currentUserId, "PENDING"))
                .thenReturn(true);

        UserProfileDTO dto = userProfileService.getUserProfile(targetUserId, currentUserId);

        assertThat(dto).isNotNull();
        assertThat(dto.getRelationshipStatus()).isEqualTo("WAITING");
        assertThat(dto.getIsFriend()).isFalse();
    }

    @Test
    @DisplayName("determineRelationship returns PENDING when current user sent a pending request")
    void getProfile_shouldReturnPending_whenCurrentUserSentRequest() {
        Integer targetUserId = 10;
        Integer currentUserId = 20;

        User targetUser = new User();
        targetUser.setId(targetUserId);
        targetUser.setUsername("alice");

        UserProfile profile = new UserProfile();
        profile.setUser(targetUser);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(userProfileRepository.findByUserId(targetUserId)).thenReturn(Optional.of(profile));
        when(publicProfileCache.find(targetUserId)).thenReturn(Optional.of(
                new PublicProfileFragment(targetUserId, "Alice Wonderland", "avatar.png", "cover.png")
        ));
        when(userHobbyRepository.findByUserId(targetUserId)).thenReturn(Collections.emptyList());

        when(friendRepository.existsByUserIdAndFriendId(currentUserId, targetUserId)).thenReturn(false);
        when(friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(currentUserId, targetUserId, "PENDING"))
                .thenReturn(true);

        UserProfileDTO dto = userProfileService.getUserProfile(targetUserId, currentUserId);

        assertThat(dto).isNotNull();
        assertThat(dto.getRelationshipStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("getOrCreateDirectChat recovers gracefully on concurrent creation DataIntegrityViolationException")
    void getOrCreateDirectChat_shouldRecoverOnDataIntegrityViolation() {
        User u1 = new User();
        u1.setId(1);
        u1.setUsername("user1");

        User u2 = new User();
        u2.setId(2);
        u2.setUsername("user2");

        String pairKey = "direct:1:2";

        // Initial check: not found yet
        when(chatRoomRepository.findByCanonicalPairKey(pairKey))
                .thenReturn(Optional.empty()) // Initial find
                .thenReturn(Optional.empty()) // Double check in synchronized
                .thenReturn(Optional.of(createExistingRoom(pairKey, 100L))); // Recovery find after collision

        when(chatRoomMemberRepository.findByUser_Id(1)).thenReturn(Collections.emptyList());

        // Concurrent transaction inserted the row first, causing DataIntegrityViolationException
        when(chatRoomRepository.save(any(ChatRoom.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        ChatRoomDTO dto = chatRoomService.getOrCreateDirectChat(u1, u2);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(100L);

        // Verify recovery query was executed
        verify(chatRoomRepository, times(3)).findByCanonicalPairKey(pairKey);
    }

    private ChatRoom createExistingRoom(String pairKey, Long id) {
        ChatRoom room = new ChatRoom();
        room.setId(id);
        room.setType("DIRECT");
        room.setCanonicalPairKey(pairKey);
        room.setFirebaseRoomKey("firebase-" + id);
        return room;
    }
}
