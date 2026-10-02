package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.AiModerationResult;
import org.example.connectcg_be.dto.CreatePostRequest;
import org.example.connectcg_be.dto.GroupPostDTO;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.PostMediaRepository;
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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostP2BusinessRulesTest {
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserAvatarRepository userAvatarRepository;
    @Mock private PostMediaRepository postMediaRepository;
    @Mock private ReactionRepository reactionRepository;
    @Mock private PostRealtimeService postRealtimeService;
    @Mock private PostAccessPolicy postAccessPolicy;
    @Mock private AiModerationService aiModerationService;
    @Mock private NotificationService notificationService;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private PostServiceImpl postService;

    @Test
    void deletingAShareSynchronizesRootCountAndIsIdempotent() {
        User author = new User();
        author.setId(2);
        author.setRole("USER");
        author.setUsername("author");

        Post root = new Post();
        root.setId(10);
        root.setAuthor(author);
        root.setShareCount(1);
        root.setReactCount(0);
        root.setCommentCount(0);
        root.setIsDeleted(false);

        Post share = new Post();
        share.setId(20);
        share.setAuthor(author);
        share.setOriginalPost(root);
        share.setIsDeleted(false);

        when(postRepository.findById(20)).thenReturn(Optional.of(share));
        when(userRepository.findById(2)).thenReturn(Optional.of(author));
        when(postRepository.countByOriginalPostIdAndIsDeletedFalse(10)).thenReturn(0L);

        postService.deletePost(20, 2);
        postService.deletePost(20, 2);

        assertTrue(share.getIsDeleted());
        verify(postRepository, times(1)).updateShareCount(10, 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sharePostExecutesInsideTransactionAndModeratesCaption() {
        User author = new User();
        author.setId(2);
        author.setRole("USER");
        author.setUsername("sharer");

        User originalAuthor = new User();
        originalAuthor.setId(1);
        originalAuthor.setRole("USER");
        originalAuthor.setUsername("originalAuthor");

        Post root = new Post();
        root.setId(10);
        root.setAuthor(originalAuthor);
        root.setIsDeleted(false);
        root.setVisibility("PUBLIC");

        when(postRepository.findById(10)).thenReturn(Optional.of(root));
        when(userRepository.findById(2)).thenReturn(Optional.of(author));
        when(aiModerationService.checkPostContent("Check this out!"))
                .thenReturn(new AiModerationResult(0.1, "SAFE", "Nội dung hợp lệ"));

        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            p.setId(99);
            return p;
        });
        when(postRepository.countByOriginalPostIdAndIsDeletedFalse(10)).thenReturn(1L);

        CreatePostRequest request = new CreatePostRequest();
        request.setContent("Check this out!");
        request.setVisibility("PUBLIC");

        GroupPostDTO dto = postService.sharePost(10, request, 2);

        assertNotNull(dto);
        assertEquals(99, dto.getId());
        verify(aiModerationService).checkPostContent("Check this out!");
        verify(transactionTemplate).execute(any(TransactionCallback.class));
        verify(postRepository).save(any(Post.class));
        verify(postRepository).updateShareCount(10, 1);
        verify(postRealtimeService).publishPostEvent(any(Post.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPostExecutesInsideTransactionWithAiModeration() {
        User author = new User();
        author.setId(3);
        author.setRole("USER");
        author.setUsername("john");

        when(userRepository.findById(3)).thenReturn(Optional.of(author));
        when(aiModerationService.checkPostContent("Hello world"))
                .thenReturn(new AiModerationResult(0.1, "SAFE", "Nội dung hợp lệ"));

        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            p.setId(100);
            return p;
        });

        CreatePostRequest request = new CreatePostRequest();
        request.setContent("Hello world");
        request.setVisibility("PUBLIC");

        Post savedPost = postService.createPost(request, 3);

        assertNotNull(savedPost);
        assertEquals("APPROVED", savedPost.getStatus());
        verify(aiModerationService).checkPostContent("Hello world");
        verify(transactionTemplate).execute(any(TransactionCallback.class));
        verify(postRepository).save(any(Post.class));
    }
}
