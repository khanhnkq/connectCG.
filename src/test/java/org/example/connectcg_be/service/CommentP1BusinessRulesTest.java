package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.CreateCommentRequest;
import org.example.connectcg_be.entity.Comment;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.CommentRepository;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.service.impl.CommentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentP1BusinessRulesTest {
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private PostAccessPolicy postAccessPolicy;

    @InjectMocks
    private CommentServiceImpl commentService;

    @Test
    void createCommentRequiresPostAccess() {
        Post post = post(10);
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("comment");
        when(postRepository.findById(10)).thenReturn(Optional.of(post));
        doThrow(new AccessDeniedException("forbidden"))
                .when(postAccessPolicy).requireCanView(post, 2);

        assertThrows(
                AccessDeniedException.class,
                () -> commentService.createComment(10, 2, request));
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void replyParentMustBelongToSamePost() {
        Post targetPost = post(10);
        Post otherPost = post(20);
        User commenter = user(2);
        Comment parent = new Comment();
        parent.setId(30);
        parent.setPost(otherPost);
        parent.setAuthor(user(3));
        parent.setIsDeleted(false);
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("reply");
        request.setParentId(30);

        when(postRepository.findById(10)).thenReturn(Optional.of(targetPost));
        when(userRepository.findById(2)).thenReturn(Optional.of(commenter));
        when(userProfileRepository.findByUserId(2)).thenReturn(Optional.empty());
        when(commentRepository.findById(30)).thenReturn(Optional.of(parent));

        assertThrows(RuntimeException.class, () -> commentService.createComment(10, 2, request));
        verify(commentRepository, never()).save(any(Comment.class));
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

    private User user(Integer id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        return user;
    }
}
