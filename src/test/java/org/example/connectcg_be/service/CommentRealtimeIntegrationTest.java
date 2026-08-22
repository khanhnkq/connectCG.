package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.CommentEventDTO;
import org.example.connectcg_be.dto.CommentDTO;
import org.example.connectcg_be.dto.CreateCommentRequest;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.realtime.RealtimeEventPublisher;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommentRealtimeIntegrationTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @MockBean
    private RealtimeEventPublisher realtimeEventPublisher;

    @Test
    void delegatesCreateAndDeleteEventsToPublisherWithoutDoubleDeferringThem() {
        User author = saveUser();
        Post post = savePost(author);
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Realtime comment");

        CommentDTO created = commentService.createComment(post.getId(), author.getId(), request);

        verify(realtimeEventPublisher).sendToTopic(
                eq("/topic/posts/" + post.getId() + "/comments"),
                argThat(payload -> payload instanceof CommentEventDTO event
                        && "CREATED".equals(event.getAction())
                        && post.getId().equals(event.getPostId())
                        && event.getComment() != null
                        && "Realtime comment".equals(event.getComment().getContent())
                        && event.getNewCommentCount() == 1));

        clearInvocations(realtimeEventPublisher);
        commentService.deleteComment(post.getId(), created.getId(), author.getId());

        verify(realtimeEventPublisher).sendToTopic(
                eq("/topic/posts/" + post.getId() + "/comments"),
                argThat(payload -> payload instanceof CommentEventDTO event
                        && "DELETED".equals(event.getAction())
                        && post.getId().equals(event.getPostId())
                        && created.getId().equals(event.getCommentId())
                        && event.getNewCommentCount() == 0));
    }

    private User saveUser() {
        User user = new User();
        user.setUsername("comment_realtime_author");
        user.setEmail("comment_realtime_author@example.com");
        user.setPasswordHash("test-password-hash");
        user.setRole("USER");
        user.setIsEnabled(true);
        user.setIsLocked(false);
        user.setIsDeleted(false);
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    private Post savePost(User author) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent("Post for realtime comments");
        post.setVisibility("PUBLIC");
        post.setStatus("APPROVED");
        post.setIsDeleted(false);
        post.setCommentCount(0);
        post.setReactCount(0);
        post.setShareCount(0);
        post.setCreatedAt(Instant.now());
        post.setUpdatedAt(Instant.now());
        return postRepository.save(post);
    }
}
