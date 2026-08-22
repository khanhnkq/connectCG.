package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.CommentEventDTO;
import org.example.connectcg_be.dto.PostEventDTO;
import org.example.connectcg_be.entity.Group;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.realtime.RealtimeEventPublisher;
import org.example.connectcg_be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostRealtimeServiceTest {
    @Mock
    private RealtimeEventPublisher publisher;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostRealtimeService realtimeService;

    @Test
    void pendingGroupPostIsSentOnlyToAuthorizedPostTopics() {
        Group group = new Group();
        group.setId(5);
        Post post = new Post();
        post.setId(10);
        post.setGroup(group);
        post.setStatus("PENDING");
        PostEventDTO event = new PostEventDTO("CREATED", null, 10);

        realtimeService.publishPostEvent(post, event);

        verify(publisher).sendToTopic("/topic/groups/5/posts/pending", event);
        verify(publisher, never()).sendToTopic(eq("/topic/posts"), any());
    }

    @Test
    void commentContentUsesPostSpecificTopic() {
        Post post = new Post();
        post.setId(10);
        CommentEventDTO event = new CommentEventDTO();

        realtimeService.publishCommentEvent(post, event);

        verify(publisher).sendToTopic("/topic/posts/10/comments", event);
        verify(publisher, never()).sendToTopic(eq("/topic/comments"), any());
    }
}
