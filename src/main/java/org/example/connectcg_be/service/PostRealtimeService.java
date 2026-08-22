package org.example.connectcg_be.service;

import lombok.RequiredArgsConstructor;
import org.example.connectcg_be.dto.CommentEventDTO;
import org.example.connectcg_be.dto.MembershipEventDTO;
import org.example.connectcg_be.dto.PostEventDTO;
import org.example.connectcg_be.dto.ReactionEventDTO;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.realtime.RealtimeEventPublisher;
import org.example.connectcg_be.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostRealtimeService {
    private final RealtimeEventPublisher publisher;
    private final UserRepository userRepository;

    public void publishPostEvent(Post post, PostEventDTO event) {
        String postTopic = "/topic/posts/" + post.getId() + "/updates";

        if ("APPROVED".equals(post.getStatus())) {
            publisher.sendToTopic(postTopic, event);
            if (post.getGroup() != null) {
                publisher.sendToTopic("/topic/groups/" + post.getGroup().getId() + "/posts", event);
            } else if ("PUBLIC".equals(post.getVisibility())) {
                publisher.sendToTopic("/topic/posts", event);
            }
            return;
        }

        PostEventDTO removalEvent = new PostEventDTO("DELETED", null, post.getId());
        publisher.sendToTopic(postTopic, removalEvent);

        if (post.getGroup() != null) {
            Integer groupId = post.getGroup().getId();
            publisher.sendToTopic("/topic/groups/" + groupId + "/posts", removalEvent);
            publisher.sendToTopic("/topic/groups/" + groupId + "/posts/pending", event);
        } else {
            if ("REJECTED".equals(post.getStatus())) {
                publisher.sendToTopic("/topic/posts", removalEvent);
            }
            publishToSystemAdmins("/queue/post-moderation", event);
        }
    }

    public void publishCommentEvent(Post post, CommentEventDTO event) {
        publisher.sendToTopic("/topic/posts/" + post.getId() + "/comments", event);
    }

    public void publishReactionEvent(Post post, ReactionEventDTO event) {
        publisher.sendToTopic("/topic/posts/" + post.getId() + "/reactions", event);
    }

    public void publishMembershipEvent(Integer groupId, MembershipEventDTO event) {
        boolean adminOnly = "REQUESTED".equals(event.getAction()) || "INVITED".equals(event.getAction());
        String suffix = adminOnly ? "/membership/pending" : "/membership";
        publisher.sendToTopic("/topic/groups/" + groupId + suffix, event);
    }

    private void publishToSystemAdmins(String destination, Object event) {
        for (User admin : userRepository.findByRole("ADMIN")) {
            publisher.sendToUser(admin.getUsername(), destination, event);
        }
    }
}
