package org.example.connectcg_be.service.impl;

import jakarta.transaction.Transactional;
import org.example.connectcg_be.dto.ReactionEventDTO;
import org.example.connectcg_be.entity.Notification;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.Reaction;
import org.example.connectcg_be.entity.ReactionId;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.ReactionRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.service.NotificationService;
import org.example.connectcg_be.service.ReactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
public class ReactionServiceImpl implements ReactionService {

    private static final Set<String> VALID_REACTION_TYPES =
            Set.of("LIKE", "LOVE", "HAHA", "WOW", "SAD", "ANGRY");

    @Autowired
    private ReactionRepository reactionRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private org.example.connectcg_be.repository.UserProfileRepository userProfileRepository;
    @Autowired
    private org.example.connectcg_be.service.PostAccessPolicy postAccessPolicy;
    @Autowired
    private org.example.connectcg_be.service.PostRealtimeService postRealtimeService;

    @Override
    @Transactional
    @Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void reactToPost(Integer postId, Integer userId, String type) {
        if (type == null || !VALID_REACTION_TYPES.contains(type)) {
            throw new IllegalArgumentException("Loại cảm xúc không hợp lệ");
        }
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại"));
        postAccessPolicy.requireCanView(post, userId);
        // 1. Tạo Composite Key
        ReactionId id = new ReactionId(userId, postId);
        // 2. Kiểm tra xem đã tồn tại chưa
        Optional<Reaction> existingReaction = reactionRepository.findById(id);
        if (existingReaction.isPresent()) {
            // Update reaction type (VD: LIKE -> LOVE)
            Reaction reaction = existingReaction.get();
            reaction.setType(type);
            reactionRepository.save(reaction);
        } else {
            // Tạo mới
            // Lấy Post và User proxy (getReference để tối ưu query)
            User user = userRepository.getReferenceById(userId);
            Reaction reaction = new Reaction();
            reaction.setId(id);
            reaction.setPost(post);
            reaction.setUser(user);
            reaction.setType(type);

            reactionRepository.save(reaction);

            // Gửi thông báo cho chủ bài viết
            if (!userId.equals(post.getAuthor().getId())) {
                Notification notification = new Notification();
                notification.setUser(post.getAuthor());
                notification.setActor(user);
                notification.setType("POST_REACTION");
                notification.setTargetType("POST");
                notification.setTargetId(postId);
                notification.setIsRead(false);

                String actorName = userProfileRepository.findByUserId(userId)
                        .map(org.example.connectcg_be.entity.UserProfile::getFullName)
                        .orElse(user.getUsername());
                notification.setContent(actorName + " đã bày tỏ cảm xúc về bài viết của bạn.");

                notificationService.sendNotification(notification);
            }
        }

        // Broadcast realtime
        int newCount = Math.toIntExact(reactionRepository.countByPostId(postId));
        postRepository.updateReactCount(postId, newCount);
        ReactionEventDTO event = new ReactionEventDTO("REACTED", postId, userId, type, newCount);
        postRealtimeService.publishReactionEvent(post, event);
    }

    @Override
    @Transactional
    public void unreactToPost(Integer postId, Integer userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại"));
        postAccessPolicy.requireCanView(post, userId);
        ReactionId id = new ReactionId(userId, postId);
        if (reactionRepository.existsById(id)) {
            reactionRepository.deleteById(id);

            // Broadcast realtime
            int newCount = Math.toIntExact(reactionRepository.countByPostId(postId));
            postRepository.updateReactCount(postId, newCount);
            ReactionEventDTO event = new ReactionEventDTO("UNREACTED", postId, userId, null, newCount);
            postRealtimeService.publishReactionEvent(post, event);
        }
    }
}
