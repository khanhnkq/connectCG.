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

        when(commentRepository.findById(20)).thenReturn(Optional.of(root));
        when(commentRepository.findByPostIdAndIsDeletedFalseOrderByCreatedAtDesc(10))
                .thenReturn(List.of(root, child, grandchild, otherRoot));
        when(commentRepository.countByPostIdAndIsDeletedFalse(10)).thenReturn(1L);

        commentService.deleteComment(10, 20, 2);

        assertTrue(root.getIsDeleted());
        assertTrue(child.getIsDeleted());
        assertTrue(grandchild.getIsDeleted());
        verify(postRepository).updateCommentCount(10, 1);
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
