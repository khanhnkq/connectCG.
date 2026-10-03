package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.FriendDTO;
import org.example.connectcg_be.dto.ReportResponse;
import org.example.connectcg_be.dto.TungGroupMemberDTO;
import org.example.connectcg_be.dto.UserProfileDTO;
import org.example.connectcg_be.entity.*;
import org.example.connectcg_be.repository.*;
import org.example.connectcg_be.service.impl.FriendServiceImpl;
import org.example.connectcg_be.service.impl.GroupServiceImpl;
import org.example.connectcg_be.service.impl.PostServiceImpl;
import org.example.connectcg_be.service.impl.ReportServiceImpl;
import org.example.connectcg_be.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchQueryAndNPlusOneOptimizationTest {

    // --- GroupServiceImpl Mocks ---
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private UserAvatarRepository userAvatarRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserCoverRepository userCoverRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendRepository friendRepository;
    @Mock
    private FriendRequestRepository friendRequestRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private PostAccessPolicy postAccessPolicy;

    @InjectMocks
    private GroupServiceImpl groupService;

    @InjectMocks
    private UserServiceImpl userService;

    @InjectMocks
    private FriendServiceImpl friendService;

    @InjectMocks
    private ReportServiceImpl reportService;

    @InjectMocks
    private PostServiceImpl postService;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(reportService, "reportRepository", reportRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(reportService, "postRepository", postRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(reportService, "userRepository", userRepository);
    }

    @Test
    @DisplayName("GroupServiceImpl.getMembers eliminates N+1 queries using batch lookup")
    void getMembers_shouldBatchLoadAvatarsAndProfiles() {
        Integer groupId = 1;
        Integer requesterId = 10;

        User user1 = new User();
        user1.setId(101);
        user1.setUsername("alice");

        User user2 = new User();
        user2.setId(102);
        user2.setUsername("bob");

        GroupMember gm1 = new GroupMember();
        gm1.setUser(user1);
        gm1.setRole("MEMBER");
        gm1.setStatus("ACCEPTED");
        gm1.setJoinedAt(Instant.now());

        GroupMember gm2 = new GroupMember();
        gm2.setUser(user2);
        gm2.setRole("ADMIN");
        gm2.setStatus("ACCEPTED");
        gm2.setJoinedAt(Instant.now());

        when(groupMemberRepository.findAllByIdGroupIdAndStatus(groupId, "ACCEPTED"))
                .thenReturn(List.of(gm1, gm2));

        Media avatarMedia = new Media();
        avatarMedia.setUrl("http://example.com/alice.png");
        UserAvatar avatar1 = new UserAvatar();
        avatar1.setUser(user1);
        avatar1.setMedia(avatarMedia);

        when(userAvatarRepository.findCurrentByUserIds(argThat(c -> c.containsAll(List.of(101, 102)))))
                .thenReturn(List.of(avatar1));

        UserProfile profile2 = new UserProfile();
        profile2.setUser(user2);
        profile2.setFullName("Bob Builder");

        when(userProfileRepository.findAllByUserIdIn(argThat(c -> c.containsAll(List.of(101, 102)))))
                .thenReturn(List.of(profile2));

        List<TungGroupMemberDTO> members = groupService.getMembers(groupId, requesterId);

        assertThat(members).hasSize(2);
        // Alice has custom avatar, fallback fullName to username
        assertThat(members.get(0).getUsername()).isEqualTo("alice");
        assertThat(members.get(0).getFullName()).isEqualTo("alice");
        assertThat(members.get(0).getAvatarUrl()).isEqualTo("http://example.com/alice.png");

        // Bob has default avatar, custom profile fullName
        assertThat(members.get(1).getUsername()).isEqualTo("bob");
        assertThat(members.get(1).getFullName()).isEqualTo("Bob Builder");
        assertThat(members.get(1).getAvatarUrl()).contains("flaticon.com");

        // Verify batch queries called exactly ONCE
        verify(userAvatarRepository, times(1)).findCurrentByUserIds(any());
        verify(userProfileRepository, times(1)).findAllByUserIdIn(any());
        verify(userAvatarRepository, never()).findByUserIdAndIsCurrentTrue(any());
        verify(userProfileRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("UserServiceImpl.getAllUsersPaged batch loads profiles, avatars, covers, and counts")
    void getAllUsersPaged_shouldBatchLoadEntitiesAndCounts() {
        Pageable pageable = PageRequest.of(0, 10);

        User u1 = new User();
        u1.setId(1);
        u1.setUsername("u1");
        u1.setEmail("u1@test.com");
        u1.setRole("USER");

        User u2 = new User();
        u2.setId(2);
        u2.setUsername("u2");
        u2.setEmail("u2@test.com");
        u2.setRole("ADMIN");

        Page<User> userPage = new PageImpl<>(List.of(u1, u2), pageable, 2);
        when(userRepository.findByFilters(null, null, pageable)).thenReturn(userPage);

        when(userProfileRepository.findAllByUserIdIn(anyCollection())).thenReturn(List.of());
        when(userAvatarRepository.findCurrentByUserIds(anyCollection())).thenReturn(List.of());
        when(userCoverRepository.findCurrentByUserIds(anyCollection())).thenReturn(List.of());

        List<Object[]> postCounts = List.of(new Object[]{1, 15L}, new Object[]{2, 25L});
        when(postRepository.countPostsByAuthorIds(anyCollection())).thenReturn(postCounts);

        List<Object[]> friendCounts = List.of(new Object[]{1, 8L}, new Object[]{2, 12L});
        when(friendRepository.countFriendsByUserIds(anyCollection())).thenReturn(friendCounts);

        Page<UserProfileDTO> result = userService.getAllUsersPaged(null, null, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getPostsCount()).isEqualTo(15);
        assertThat(result.getContent().get(0).getFriendsCount()).isEqualTo(8);
        assertThat(result.getContent().get(1).getPostsCount()).isEqualTo(25);
        assertThat(result.getContent().get(1).getFriendsCount()).isEqualTo(12);

        // Verify exactly one batch query for each resource
        verify(postRepository, times(1)).countPostsByAuthorIds(anyCollection());
        verify(friendRepository, times(1)).countFriendsByUserIds(anyCollection());
        verify(postRepository, never()).countByAuthorIdAndIsDeletedFalse(any());
        verify(friendRepository, never()).countByUserId(any());
    }

    @Test
    @DisplayName("FriendServiceImpl.getFriends resolves statuses in batch without N+1 queries")
    void getFriends_shouldBatchResolveStatuses() {
        Integer currentUserId = 5;
        Pageable pageable = PageRequest.of(0, 10);

        FriendDTO fSelf = new FriendDTO();
        fSelf.setId(5);

        FriendDTO fFriend = new FriendDTO();
        fFriend.setId(6);

        FriendDTO fPending = new FriendDTO();
        fPending.setId(7);

        FriendDTO fWaiting = new FriendDTO();
        fWaiting.setId(8);

        FriendDTO fStranger = new FriendDTO();
        fStranger.setId(9);

        Page<FriendDTO> friendsPage = new PageImpl<>(List.of(fSelf, fFriend, fPending, fWaiting, fStranger), pageable, 5);
        when(friendRepository.searchFriends(eq(1), isNull(), isNull(), isNull(), eq(pageable))).thenReturn(friendsPage);

        when(friendRepository.findFriendIdsByViewerIdAndFriendIdIn(eq(currentUserId), anyCollection()))
                .thenReturn(Set.of(6));
        when(friendRequestRepository.findPendingReceiverIds(eq(currentUserId), anyCollection()))
                .thenReturn(Set.of(7));
        when(friendRequestRepository.findPendingSenderIds(eq(currentUserId), anyCollection()))
                .thenReturn(Set.of(8));

        Page<FriendDTO> result = friendService.getFriends(1, currentUserId, null, null, null, pageable);

        assertThat(result.getContent().get(0).getRelationshipStatus()).isEqualTo("SELF");
        assertThat(result.getContent().get(1).getRelationshipStatus()).isEqualTo("FRIEND");
        assertThat(result.getContent().get(2).getRelationshipStatus()).isEqualTo("PENDING");
        assertThat(result.getContent().get(3).getRelationshipStatus()).isEqualTo("WAITING");
        assertThat(result.getContent().get(4).getRelationshipStatus()).isEqualTo("STRANGER");

        // Verify bulk queries called once and existsBy... never called
        verify(friendRepository, times(1)).findFriendIdsByViewerIdAndFriendIdIn(any(), anyCollection());
        verify(friendRequestRepository, times(1)).findPendingReceiverIds(any(), anyCollection());
        verify(friendRequestRepository, times(1)).findPendingSenderIds(any(), anyCollection());
        verify(friendRepository, never()).existsByUserIdAndFriendId(any(), any());
        verify(friendRequestRepository, never()).existsBySenderIdAndReceiverIdAndStatus(any(), any(), any());
    }

    @Test
    @DisplayName("ReportServiceImpl.getReportsPaginated preloads post groups in batch")
    void getReportsPaginated_shouldPreloadPostGroups() {
        Pageable pageable = PageRequest.of(0, 10);

        Report r1 = new Report();
        r1.setId(1);
        r1.setTargetType("POST");
        r1.setTargetId(101);
        r1.setStatus("PENDING");

        Report r2 = new Report();
        r2.setId(2);
        r2.setTargetType("GROUP");
        r2.setTargetId(999);
        r2.setStatus("RESOLVED");

        Page<Report> reportPage = new PageImpl<>(List.of(r1, r2), pageable, 2);
        when(reportRepository.findAll(pageable)).thenReturn(reportPage);

        Group group = new Group();
        group.setId(88);

        Post post1 = new Post();
        post1.setId(101);
        post1.setGroup(group);

        when(postRepository.findAllById(List.of(101))).thenReturn(List.of(post1));

        Page<ReportResponse> result = reportService.getReportsPaginated(pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getGroupId()).isEqualTo(88);
        assertThat(result.getContent().get(1).getGroupId()).isEqualTo(999);

        // Verify batch query called once
        verify(postRepository, times(1)).findAllById(List.of(101));
        verify(postRepository, never()).findById(any());
    }

    @Test
    @DisplayName("PostServiceImpl.countPostsVisibleToUser filters posts using postAccessPolicy consistently")
    void countPostsVisibleToUser_shouldFilterUsingPolicy() {
        Integer authorId = 10;
        Integer viewerId = 20;

        Post p1 = new Post();
        p1.setId(101);
        Post p2 = new Post();
        p2.setId(102);

        when(postRepository.findAllByAuthorIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(authorId, "APPROVED"))
                .thenReturn(List.of(p1, p2));
        when(postAccessPolicy.canView(p1, viewerId)).thenReturn(true);
        when(postAccessPolicy.canView(p2, viewerId)).thenReturn(false);

        int count = postService.countPostsVisibleToUser(authorId, viewerId);
        assertThat(count).isEqualTo(1);

        verify(postRepository).findAllByAuthorIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(authorId, "APPROVED");
        verify(postAccessPolicy).canView(p1, viewerId);
        verify(postAccessPolicy).canView(p2, viewerId);
    }
}
