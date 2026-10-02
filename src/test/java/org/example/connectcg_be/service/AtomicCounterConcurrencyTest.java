package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.CreateCommentRequest;
import org.example.connectcg_be.dto.ReactionEventDTO;
import org.example.connectcg_be.entity.Comment;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.Reaction;
import org.example.connectcg_be.entity.ReactionId;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.CommentRepository;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.ReactionRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.service.impl.CommentServiceImpl;
import org.example.connectcg_be.service.impl.ReactionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtomicCounterConcurrencyTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PostAccessPolicy postAccessPolicy;

    @Mock
    private PostRealtimeService postRealtimeService;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private ReactionServiceImpl reactionService;

    @InjectMocks
    private CommentServiceImpl commentService;

    @Test
    @DisplayName("Switching reaction type (e.g. LIKE -> LOVE) does not alter react count")
    void switchingReactionTypeDoesNotChangeReactCount() {
        Post post = createPost(100, 1);
        User user = createUser(2);
        ReactionId reactionId = new ReactionId(2, 100);

        Reaction existingReaction = new Reaction();
        existingReaction.setId(reactionId);
        existingReaction.setType("LIKE");
        existingReaction.setPost(post);
        existingReaction.setUser(user);

        when(postRepository.findById(100)).thenReturn(Optional.of(post));
        when(reactionRepository.findById(reactionId)).thenReturn(Optional.of(existingReaction));
        when(postRepository.findReactCountById(100)).thenReturn(5);

        reactionService.reactToPost(100, 2, "LOVE");

        // Verifications:
        // 1. Existing reaction type updated to LOVE
        verify(reactionRepository).save(existingReaction);
        // 2. adjustReactCount must NEVER be called because total reactions did not change
        verify(postRepository, never()).adjustReactCount(any(), anyInt());
        verify(postRealtimeService).publishReactionEvent(eq(post), any(ReactionEventDTO.class));
    }

    @Test
    @DisplayName("Unreact atomically decrements react count by 1")
    void unreactAtomicallyDecrementsReactCount() {
        Post post = createPost(100, 1);
        ReactionId reactionId = new ReactionId(2, 100);

        when(postRepository.findById(100)).thenReturn(Optional.of(post));
        when(reactionRepository.existsById(reactionId)).thenReturn(true);
        when(postRepository.findReactCountById(100)).thenReturn(4);

        reactionService.unreactToPost(100, 2);

        verify(reactionRepository).deleteById(reactionId);
        verify(postRepository).adjustReactCount(100, -1);
    }

    @Test
    @DisplayName("Replying to deleted parent comment is blocked under pessimistic lock")
    void replyToDeletedParentIsBlockedUnderLock() {
        Post post = createPost(100, 1);
        User commenter = createUser(2);
        Comment deletedParent = new Comment();
        deletedParent.setId(50);
        deletedParent.setPost(post);
        deletedParent.setIsDeleted(true);

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Reply to deleted parent");
        request.setParentId(50);

        when(postRepository.findById(100)).thenReturn(Optional.of(post));
        when(userRepository.findById(2)).thenReturn(Optional.of(commenter));
        when(userProfileRepository.findByUserId(2)).thenReturn(Optional.empty());
        when(commentRepository.findByIdForUpdate(50)).thenReturn(Optional.of(deletedParent));

        assertThrows(RuntimeException.class, () -> commentService.createComment(100, 2, request));
        verify(commentRepository, never()).save(any(Comment.class));
        verify(postRepository, never()).adjustCommentCount(any(), anyInt());
    }

    @Test
    @DisplayName("Deleting comment tree atomically decrements post comment count by exact subtree size")
    void deletingCommentTreeAtomicallyDecrementsSubtreeSize() {
        Post post = createPost(100, 1);
        User author = createUser(2);

        Comment root = new Comment();
        root.setId(10);
        root.setPost(post);
        root.setAuthor(author);
        root.setIsDeleted(false);

        Comment child = new Comment();
        child.setId(11);
        child.setPost(post);
        child.setParent(root);
        child.setAuthor(author);
        child.setIsDeleted(false);

        when(commentRepository.findByIdForUpdate(10)).thenReturn(Optional.of(root));
        when(commentRepository.findByPostIdAndIsDeletedFalseOrderByCreatedAtDesc(100))
                .thenReturn(List.of(root, child));
        when(postRepository.findCommentCountById(100)).thenReturn(0);

        commentService.deleteComment(100, 10, 2);

        assertTrue(root.getIsDeleted());
        assertTrue(child.getIsDeleted());
        // Exact subtree size is 2 (root + child)
        verify(postRepository).adjustCommentCount(100, -2);
    }

    private Post createPost(Integer id, Integer authorId) {
        Post post = new Post();
        post.setId(id);
        post.setAuthor(createUser(authorId));
        post.setStatus("APPROVED");
        post.setVisibility("PUBLIC");
        post.setIsDeleted(false);
        post.setReactCount(5);
        post.setCommentCount(2);
        post.setCreatedAt(Instant.now());
        return post;
    }

    private User createUser(Integer id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        return user;
    }
}
