package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.CreateCommentRequest;
import org.example.connectcg_be.entity.Comment;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.CommentRepository;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.UserAvatarRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.service.impl.CommentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentP2BusinessRulesTest {
    @Mock private CommentRepository commentRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserAvatarRepository userAvatarRepository;
    @Mock private PostAccessPolicy postAccessPolicy;
    @Mock private PostRealtimeService postRealtimeService;
    @Mock private NotificationService notificationService;
    @Mock private MediaService mediaService;

    @InjectMocks private CommentServiceImpl commentService;

    @Test
    void emptyCommentIsRejectedBeforeDatabaseMutation() {
        CreateCommentRequest request = new CreateCommentRequest();

        assertThrows(IllegalArgumentException.class, () -> commentService.createComment(10, 2, request));
        verify(commentRepository, never()).save(org.mockito.ArgumentMatchers.any(Comment.class));
    }

    @Test
    void deletingRootCommentSoftDeletesItsWholeVisibleSubtreeAndSynchronizesCount() {
        Post post = post(10);
        Comment root = comment(20, post, null);
        Comment child = comment(21, post, root);
        Comment grandchild = comment(22, post, child);
        Comment otherRoot = comment(23, post, null);

        when(commentRepository.findByIdForUpdate(20)).thenReturn(Optional.of(root));
        when(commentRepository.findByPostIdAndIsDeletedFalseOrderByCreatedAtDesc(10))
                .thenReturn(List.of(root, child, grandchild, otherRoot));
        when(postRepository.findCommentCountById(10)).thenReturn(1);

        commentService.deleteComment(10, 20, 2);

        assertTrue(root.getIsDeleted());
        assertTrue(child.getIsDeleted());
        assertTrue(grandchild.getIsDeleted());
        verify(postRepository).adjustCommentCount(10, -3);
    }

    @Test
    void createComment_replyToPostAuthorsComment_sendsOnlyCommentReplyNotification() {
        Post post = post(10); // author is user(1)
        Comment parent = comment(20, post, null);
        parent.setAuthor(user(1)); // author of parent comment is also user(1)

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("This is a reply to author");
        request.setParentId(20);

        when(postRepository.findById(10)).thenReturn(Optional.of(post));
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2))); // commenter is user(2)
        when(commentRepository.findByIdForUpdate(20)).thenReturn(Optional.of(parent));
        when(commentRepository.save(org.mockito.ArgumentMatchers.any(Comment.class)))
                .thenAnswer(inv -> {
                    Comment c = inv.getArgument(0);
                    c.setId(99);
                    return c;
                });
        when(postRepository.findCommentCountById(10)).thenReturn(2);

        commentService.createComment(10, 2, request);

        org.mockito.ArgumentCaptor<org.example.connectcg_be.entity.Notification> notiCaptor =
                org.mockito.ArgumentCaptor.forClass(org.example.connectcg_be.entity.Notification.class);
        verify(notificationService, org.mockito.Mockito.times(1)).sendNotification(notiCaptor.capture());

        org.example.connectcg_be.entity.Notification sentNotification = notiCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("COMMENT_REPLY", sentNotification.getType());
        org.junit.jupiter.api.Assertions.assertEquals(1, sentNotification.getUser().getId());
    }

    private Post post(Integer id) {
        Post post = new Post();
        post.setId(id);
        post.setAuthor(user(1));
        post.setStatus("APPROVED");
        post.setVisibility("PUBLIC");
        post.setIsDeleted(false);
        return post;
    }

    private Comment comment(Integer id, Post post, Comment parent) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setPost(post);
        comment.setParent(parent);
        comment.setAuthor(user(2));
        comment.setIsDeleted(false);
        return comment;
    }

    private User user(Integer id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        return user;
    }
}
