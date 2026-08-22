package org.example.connectcg_be.service.impl;

import jakarta.transaction.Transactional;
import org.example.connectcg_be.dto.CommentDTO;
import org.example.connectcg_be.dto.CommentEventDTO;
import org.example.connectcg_be.dto.CreateCommentRequest;
import org.example.connectcg_be.entity.*;
import org.example.connectcg_be.repository.*;
import org.example.connectcg_be.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Service
public class CommentServiceImpl implements CommentService {

    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserAvatarRepository userAvatarRepository;
    @Autowired
    private org.example.connectcg_be.service.NotificationService notificationService;
    @Autowired
    private org.example.connectcg_be.service.MediaService mediaService;
    @Autowired
    private org.example.connectcg_be.service.PostAccessPolicy postAccessPolicy;
    @Autowired
    private org.example.connectcg_be.service.PostRealtimeService postRealtimeService;

    private CommentDTO convertToDTO(Comment comment) {
        CommentDTO dto = new CommentDTO();
        dto.setId(comment.getId());
        dto.setContent(comment.getContent());
        dto.setCreatedAt(comment.getCreatedAt());
        dto.setAuthorId(comment.getAuthor().getId());

        // Lấy tên
        dto.setAuthorName(comment.getAuthor().getUsername());
        userProfileRepository.findByUserId(comment.getAuthor().getId())
                .ifPresent(p -> dto.setAuthorName(p.getFullName()));

        // Lấy avatar
        UserAvatar avatar = userAvatarRepository
                .findByUserIdAndIsCurrentTrue(comment.getAuthor().getId());
        if (avatar != null && avatar.getMedia() != null) {
            dto.setAuthorAvatar(avatar.getMedia().getUrl());
        } else {
            dto.setAuthorAvatar("https://cdn-icons-png.flaticon.com/512/149/149071.png");
        }

        dto.setParentId(comment.getParent() != null ? comment.getParent().getId() : null);

        if (comment.getMedia() != null) {
            dto.setImageUrl(comment.getMedia().getUrl());
        }

        return dto;
    }

    private int getCommentDepth(Comment comment) {
        int depth = 0;
        Comment current = comment;
        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<CommentDTO> getCommentsByPostId(Integer postId, Integer userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết"));
        postAccessPolicy.requireCanView(post, userId);
        List<Comment> allComments = commentRepository
                .findByPostIdAndIsDeletedFalseOrderByCreatedAtDesc(postId);
        Map<Integer, CommentDTO> dtoMap = new HashMap<>();
        List<CommentDTO> rootComments = new ArrayList<>();
        // convert toan bo comment sang dto
        for (Comment c : allComments) {
            CommentDTO dto = convertToDTO(c);
            dtoMap.put(c.getId(), dto);
        }
        // bat dau build tree comment
        for (Comment c : allComments) {
            CommentDTO dto = dtoMap.get(c.getId()); // lay tung comment

            if (c.getParent() == null) { // null => comment cha, cap 1
                // Comment gốc (cấp 1)
                rootComments.add(dto);
            } else {
                // Reply -> Tìm cha và thêm vào danh sách replies
                CommentDTO parentDTO = dtoMap.get(c.getParent().getId());
                if (parentDTO != null) {
                    parentDTO.getReplies().add(dto); // ==> them comment vao danh sach reply cua comment cha
                }
            }
        }

        return rootComments;
    }

    @Override
    @Transactional
    @Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public CommentDTO createComment(Integer postId, Integer userId, CreateCommentRequest request) {
        validateCommentPayload(request);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết"));
        postAccessPolicy.requireCanView(post, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(user);
        comment.setContent(request.getContent());
        comment.setCreatedAt(Instant.now());
        comment.setIsDeleted(false);

        User commenter = user;
        String commenterName = userProfileRepository.findByUserId(commenter.getId())
                .map(org.example.connectcg_be.entity.UserProfile::getFullName)
                .orElse(commenter.getUsername());

        if (request.getParentId() != null) {
            Comment parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy comment cha"));
            if (parent.getPost() == null || !postId.equals(parent.getPost().getId())) {
                throw new RuntimeException("Comment cha không thuộc bài viết này");
            }
            if (Boolean.TRUE.equals(parent.getIsDeleted())) {
                throw new RuntimeException("Comment cha đã bị xóa");
            }
            // Kiểm tra độ sâu (max 3 cấp)
            int depth = getCommentDepth(parent);
            if (depth >= 2) {
                // Nếu đã ở cấp 3 -> Không cho reply thêm
                // Hoặc có thể gán về cùng cấp với parent
                throw new RuntimeException("Đã đạt giới hạn comment 3 cấp");
            }

            comment.setParent(parent);

            // Gửi thông báo cho chủ comment cha (Reply Notification)
            if (!commenter.getId().equals(parent.getAuthor().getId())) {
                Notification notification = new Notification();
                notification.setUser(parent.getAuthor());
                notification.setActor(commenter);
                notification.setType("COMMENT_REPLY");
                notification.setTargetType("POST");
                notification.setTargetId(postId);
                notification.setIsRead(false);
                notification.setContent(commenterName + " đã phản hồi bình luận của bạn.");
                notificationService.sendNotification(notification);
            }
        }

        // --- Xử lý Media (Ảnh) cho comment ---
        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            Media media = mediaService.resolveOwnedMedia(request.getImageUrl(), user.getId());
            comment.setMedia(media);
        }

        Comment saved = commentRepository.save(comment);

        // Gửi thông báo cho chủ bài viết (Comment Notification)
        if (!commenter.getId().equals(post.getAuthor().getId())) {
            Notification notification = new Notification();
            notification.setUser(post.getAuthor());
            notification.setActor(commenter);
            notification.setType("POST_COMMENT");
            notification.setTargetType("POST");
            notification.setTargetId(postId);
            notification.setIsRead(false);

            String truncatedContent = saved.getContent();
            if (truncatedContent == null || truncatedContent.isBlank()) {
                notification.setContent(commenterName + " đã bình luận bằng một hình ảnh.");
            } else {
                if (truncatedContent.length() > 50) {
                    truncatedContent = truncatedContent.substring(0, 47) + "...";
                }
                notification.setContent(
                        commenterName + " đã bình luận về bài viết của bạn: \"" + truncatedContent + "\"");
            }
            notificationService.sendNotification(notification);
        }

        // The publisher schedules durable events after the transaction commits.
        CommentDTO dto = convertToDTO(saved);
        int newCommentCount = Math.toIntExact(commentRepository.countByPostIdAndIsDeletedFalse(postId));
        postRepository.updateCommentCount(postId, newCommentCount);
        CommentEventDTO event = new CommentEventDTO("CREATED", postId, dto, saved.getId(), newCommentCount);
        postRealtimeService.publishCommentEvent(post, event);

        return dto;
    }

    @Override
    @Transactional
    public void deleteComment(Integer postId, Integer commentId, Integer userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy comment"));
        if (comment.getPost() == null || !postId.equals(comment.getPost().getId())) {
            throw new RuntimeException("Comment không thuộc bài viết này");
        }
        postAccessPolicy.requireCanView(comment.getPost(), userId);

        // Chỉ cho phép xóa comment của chính mình
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new RuntimeException("Bạn chỉ có thế xóa comment của chính mình");
        }

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            return;
        }

        List<Comment> activeComments = commentRepository
                .findByPostIdAndIsDeletedFalseOrderByCreatedAtDesc(postId);
        Set<Integer> deletedIds = collectSubtreeIds(activeComments, commentId);
        activeComments.stream()
                .filter(candidate -> deletedIds.contains(candidate.getId()))
                .forEach(candidate -> candidate.setIsDeleted(true));
        commentRepository.saveAll(activeComments.stream()
                .filter(candidate -> deletedIds.contains(candidate.getId()))
                .toList());

        // Lấy post để lấy thông tin cho broadcast
        Post post = comment.getPost();

        int newCommentCount = Math.toIntExact(commentRepository.countByPostIdAndIsDeletedFalse(postId));
        postRepository.updateCommentCount(post.getId(), newCommentCount);

        // The publisher schedules durable events after the transaction commits.
        CommentEventDTO event = new CommentEventDTO("DELETED", postId, null, commentId, newCommentCount);
        postRealtimeService.publishCommentEvent(post, event);
    }

    private void validateCommentPayload(CreateCommentRequest request) {
        boolean hasContent = request != null && request.getContent() != null && !request.getContent().isBlank();
        boolean hasImage = request != null && request.getImageUrl() != null && !request.getImageUrl().isBlank();
        if (!hasContent && !hasImage) {
            throw new IllegalArgumentException("Bình luận phải có nội dung hoặc hình ảnh");
        }
    }

    private Set<Integer> collectSubtreeIds(List<Comment> comments, Integer rootId) {
        Set<Integer> subtreeIds = new HashSet<>();
        subtreeIds.add(rootId);
        boolean changed;
        do {
            changed = false;
            for (Comment candidate : comments) {
                if (candidate.getParent() != null
                        && subtreeIds.contains(candidate.getParent().getId())
                        && subtreeIds.add(candidate.getId())) {
                    changed = true;
                }
            }
        } while (changed);
        return subtreeIds;
    }

}
