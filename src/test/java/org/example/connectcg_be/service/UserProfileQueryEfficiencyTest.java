package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.MemberSearchResponse;
import org.example.connectcg_be.cache.PublicProfileCache;
import org.example.connectcg_be.cache.PublicProfileFragment;
import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.entity.UserAvatar;
import org.example.connectcg_be.entity.UserProfile;
import org.example.connectcg_be.repository.FriendRepository;
import org.example.connectcg_be.repository.FriendRequestRepository;
import org.example.connectcg_be.repository.MediaRepository;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.UserAvatarRepository;
import org.example.connectcg_be.repository.UserCoverRepository;
import org.example.connectcg_be.repository.UserHobbyRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.service.impl.UserProfileServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileQueryEfficiencyTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private FriendRepository friendRepository;
    @Mock
    private FriendRequestRepository friendRequestRepository;
    @Mock
    private UserAvatarRepository userAvatarRepository;
    @Mock
    private UserCoverRepository userCoverRepository;
    @Mock
    private UserHobbyRepository userHobbyRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private PostService postService;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private PublicProfileCache publicProfileCache;

    @InjectMocks
    private UserProfileServiceImpl userProfileService;

    @Test
    void memberSearchLoadsAvatarsInOneBatchInsteadOfPerUser() {
        Integer currentUserId = 99;
        Pageable pageable = PageRequest.of(0, 20);
        UserProfile currentProfile = new UserProfile();
        List<MemberSearchResponse> members = List.of(
                MemberSearchResponse.builder().userId(10).username("alice").build(),
                MemberSearchResponse.builder().userId(20).username("bob").build());

        when(userProfileRepository.findByUserId(currentUserId)).thenReturn(java.util.Optional.of(currentProfile));
        when(userProfileRepository.searchMembers(
                eq(currentUserId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(members, pageable, members.size()));

        Page<MemberSearchResponse> result = userProfileService.searchMembers(
                currentUserId, null, null, null, null, null, pageable);

        assertEquals(List.of(10, 20), result.getContent().stream().map(MemberSearchResponse::getUserId).toList());
        verify(userAvatarRepository, never()).findByUserIdAndIsCurrentTrue(anyInt());
    }

    @Test
    void memberSearchMapsAvatarReturnedByBatchQuery() {
        Integer currentUserId = 99;
        Pageable pageable = PageRequest.of(0, 20);
        UserProfile currentProfile = new UserProfile();
        MemberSearchResponse member = MemberSearchResponse.builder().userId(10).username("alice").build();

        User user = new User();
        user.setId(member.getUserId());
        Media media = new Media();
        media.setId(100);
        media.setUrl("https://cdn/avatar.jpg");
        media.setType("IMAGE");
        UserAvatar avatar = new UserAvatar();
        avatar.setUser(user);
        avatar.setMedia(media);
        avatar.setIsCurrent(true);

        when(userProfileRepository.findByUserId(currentUserId)).thenReturn(java.util.Optional.of(currentProfile));
        when(userProfileRepository.searchMembers(
                eq(currentUserId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(member), pageable, 1));
        when(userAvatarRepository.findCurrentByUserIds(any())).thenReturn(List.of(avatar));

        MemberSearchResponse result = userProfileService.searchMembers(
                currentUserId, null, null, null, null, null, pageable).getContent().get(0);

        assertEquals("https://cdn/avatar.jpg", result.getAvatarUrl());
    }

    @Test
    void publicFragmentCacheHitSkipsAvatarAndCoverQueries() {
        Integer userId = 10;
        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        user.setRole("USER");
        user.setIsLocked(false);
        user.setPermanentLocked(false);
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFullName("Database Name");
        PublicProfileFragment fragment = new PublicProfileFragment(
                userId, "Cached Name", "cached-avatar", "cached-cover");

        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(profile));
        when(publicProfileCache.find(userId)).thenReturn(java.util.Optional.of(fragment));
        when(mediaRepository.findAllByUploaderIdAndIsDeletedFalseOrderByUploadedAtDesc(userId))
                .thenReturn(List.of());
        when(userHobbyRepository.findByUserId(userId)).thenReturn(List.of());
        when(postService.countPostsVisibleToUser(userId, userId)).thenReturn(1);

        org.example.connectcg_be.dto.UserProfileDTO result = userProfileService.getUserProfile(userId, userId);

        assertEquals("Cached Name", result.getFullName());
        assertEquals("cached-avatar", result.getCurrentAvatarUrl());
        assertEquals("cached-cover", result.getCurrentCoverUrl());
        assertEquals(1, result.getPostsCount());
        verify(userAvatarRepository, never()).findByUserIdAndIsCurrentTrue(userId);
        verify(userCoverRepository, never()).findByUserIdAndIsCurrentTrue(userId);
    }
}
