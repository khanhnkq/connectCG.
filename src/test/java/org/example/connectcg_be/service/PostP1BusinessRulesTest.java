package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.CreatePostRequest;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.User;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostP1BusinessRulesTest {
    @Mock
    private PostRepository postRepository;
    @Mock
    private PostAccessPolicy postAccessPolicy;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserAvatarRepository userAvatarRepository;
    @Mock
    private PostMediaRepository postMediaRepository;
    @Mock
    private ReactionRepository reactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AiModerationService aiModerationService;

    @InjectMocks
    private PostServiceImpl postService;

    @Test
    void profilePostsAreFilteredForViewerAndEnrichedWithViewerReaction() {
        Post visible = post(10, 1, "PUBLIC");
        Post hidden = post(11, 1, "PRIVATE");
        when(postRepository.findAllByAuthorIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(1, "APPROVED"))
                .thenReturn(List.of(visible, hidden));
        when(postAccessPolicy.canView(visible, 2)).thenReturn(true);
        when(postAccessPolicy.canView(hidden, 2)).thenReturn(false);

        assertEquals(List.of(10), postService.getPostsByUserId(1, 2).stream().map(dto -> dto.getId()).toList());
        verify(reactionRepository).findAllByUserIdAndPostIdIn(eq(2), any());
    }

    @Test
    void shareRequiresAccessToOriginalPost() {
        Post original = post(10, 1, "PRIVATE");
        when(postRepository.findById(10)).thenReturn(Optional.of(original));
        doThrow(new AccessDeniedException("forbidden"))
                .when(postAccessPolicy).requireCanView(original, 2);

        assertThrows(
                AccessDeniedException.class,
                () -> postService.sharePost(10, new CreatePostRequest(), 2));
        verify(aiModerationService, never()).checkPostContent(any());
    }

    @Test
    void visibleProfilePostCountUsesTheSamePolicyAsTheList() {
        Post visible = post(10, 1, "PUBLIC");
        Post hidden = post(11, 1, "PRIVATE");
        when(postRepository.findAllByAuthorIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(1, "APPROVED"))
                .thenReturn(List.of(visible, hidden));
        when(postAccessPolicy.canView(visible, 2)).thenReturn(true);
        when(postAccessPolicy.canView(hidden, 2)).thenReturn(false);

        assertEquals(1, postService.countPostsVisibleToUser(1, 2));
    }

    private Post post(Integer id, Integer authorId, String visibility) {
        User author = new User();
        author.setId(authorId);
        author.setUsername("user-" + authorId);

        Post post = new Post();
        post.setId(id);
        post.setAuthor(author);
        post.setContent("post-" + id);
        post.setVisibility(visibility);
        post.setStatus("APPROVED");
        post.setIsDeleted(false);
        post.setCreatedAt(Instant.now());
        post.setReactCount(0);
        post.setCommentCount(0);
        post.setShareCount(0);
        return post;
    }
}
