package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.AiModerationResult;
import org.example.connectcg_be.dto.CreatePostRequest;
import org.example.connectcg_be.entity.Group;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.GroupMemberRepository;
import org.example.connectcg_be.repository.PostMediaRepository;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.ReactionRepository;
import org.example.connectcg_be.repository.UserAvatarRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.service.impl.PostServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostP0BusinessRulesTest {
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AiModerationService aiModerationService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserAvatarRepository userAvatarRepository;
    @Mock
    private PostMediaRepository postMediaRepository;
    @Mock
    private ReactionRepository reactionRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PostRealtimeService postRealtimeService;
    @Mock
    private PostAccessPolicy postAccessPolicy;

    @InjectMocks
    private PostServiceImpl postService;

    @Test
    void regularUserPublicPostAlwaysRunsServerSideModeration() {
        User author = user(5, "USER");
        CreatePostRequest request = new CreatePostRequest();
        request.setContent("content requiring moderation");
        request.setVisibility("PUBLIC");

        when(userRepository.findById(5)).thenReturn(Optional.of(author));
        when(aiModerationService.checkPostContent(request.getContent()))
                .thenReturn(new AiModerationResult(0.9, "TOXIC", "unsafe"));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            saved.setId(100);
            return saved;
        });

        Post result = postService.createPost(request, 5);

        assertEquals("PENDING", result.getStatus());
        verify(aiModerationService).checkPostContent(request.getContent());
    }

    @Test
    void groupAdminCannotApprovePostFromAnotherGroup() {
        Post post = groupPost(100, 20);
        when(postRepository.findById(100)).thenReturn(Optional.of(post));

        assertThrows(RuntimeException.class, () -> postService.approveGroupPost(10, 100, 7));
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void groupAdminCannotRejectPostFromAnotherGroup() {
        Post post = groupPost(100, 20);
        when(postRepository.findById(100)).thenReturn(Optional.of(post));

        assertThrows(RuntimeException.class, () -> postService.rejectGroupPost(10, 100, 7));
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void getPostByIdEnforcesAccessPolicy() {
        Post post = groupPost(100, 20);
        when(postRepository.findById(100)).thenReturn(Optional.of(post));
        doThrow(new AccessDeniedException("forbidden"))
                .when(postAccessPolicy).requireCanView(post, 2);

        assertThrows(AccessDeniedException.class, () -> postService.getPostById(100, 2));
        verify(postAccessPolicy).requireCanView(post, 2);
        verify(userProfileRepository, never()).findAllByUserIdIn(any());
    }

    private User user(Integer id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setRole(role);
        return user;
    }

    private Post groupPost(Integer postId, Integer groupId) {
        Group group = new Group();
        group.setId(groupId);

        Post post = new Post();
        post.setId(postId);
        post.setGroup(group);
        post.setAuthor(user(5, "USER"));
        post.setStatus("PENDING");
        post.setIsDeleted(false);
        return post;
    }
}
