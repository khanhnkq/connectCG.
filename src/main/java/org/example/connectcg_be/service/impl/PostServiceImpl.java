package org.example.connectcg_be.service.impl;

import jakarta.transaction.Transactional;
import org.example.connectcg_be.dto.*;
import org.example.connectcg_be.entity.*;
import org.example.connectcg_be.repository.*;
import org.example.connectcg_be.service.GroupMemberService;
import org.example.connectcg_be.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private PostMediaRepository postMediaRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserAvatarRepository userAvatarRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.example.connectcg_be.service.AiModerationService aiModerationService;

    @Autowired
    private org.example.connectcg_be.repository.GroupRepository groupRepository;

    @Autowired
    private org.example.connectcg_be.service.MediaService mediaService;

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private GroupMemberService groupMemberService;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private org.example.connectcg_be.service.NotificationService notificationService;

    @Autowired
    private org.example.connectcg_be.service.PostAccessPolicy postAccessPolicy;

    @Autowired
    private org.example.connectcg_be.service.PostRealtimeService postRealtimeService;

    @Override
    public List<GroupPostDTO> getPendingPosts(Integer groupId, Integer userId) {
        List<Post> posts = postRepository
                .findAllByGroupIdAndStatusAndIsDeletedFalseOrderByIsPinnedDescPinnedAtDescCreatedAtDesc(groupId,
                        "PENDING");
        return convertToDTOs(posts, userId);
    }

    @Override
    public List<GroupPostDTO> getApprovedPosts(Integer groupId, Integer userId) {
        List<Post> posts = postRepository
                .findAllByGroupIdAndStatusAndIsDeletedFalseOrderByIsPinnedDescPinnedAtDescCreatedAtDesc(groupId,
                        "APPROVED");
        return convertToDTOs(posts, userId);
    }

    @Override
    public List<GroupPostDTO> getNewsfeedPosts(Integer userId) {
        List<Integer> friendIds = friendRepository.findAllFriendIds(userId);
        if (friendIds == null || friendIds.isEmpty())
            friendIds = List.of(-1);
        List<Integer> groupIds = groupMemberService.getAcceptedGroupIds(userId, "ACCEPTED");
        if (groupIds == null || groupIds.isEmpty())
            groupIds = List.of(-1);
        List<Post> posts = postRepository.findNewsfeedPosts(userId, friendIds, groupIds);
        return convertToDTOs(posts, userId);
    }

    @Override
    public org.springframework.data.domain.Page<GroupPostDTO> getNewsfeedPosts(Integer userId, int page, int size) {
        List<Integer> friendIds = friendRepository.findAllFriendIds(userId);
        if (friendIds == null || friendIds.isEmpty())
            friendIds = List.of(-1);
        List<Integer> groupIds = groupMemberService.getAcceptedGroupIds(userId, "ACCEPTED");
        if (groupIds == null || groupIds.isEmpty())
            groupIds = List.of(-1);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<Post> posts = postRepository.findNewsfeedPosts(userId, friendIds, groupIds,
                pageable);
        return convertToDTOPage(posts, userId);
    }

    @Override
    public List<GroupPostDTO> getPostsByUserId(Integer userId, Integer viewerId) {
        List<Post> posts = postRepository.findAllByAuthorIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(userId,
                "APPROVED");
        List<Post> visiblePosts = posts.stream()
                .filter(post -> postAccessPolicy.canView(post, viewerId))
                .toList();
        return convertToDTOs(visiblePosts, viewerId);
    }

    @Override
    public int countPostsVisibleToUser(Integer userId, Integer viewerId) {
        return Math.toIntExact(postRepository
                .findAllByAuthorIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(userId, "APPROVED")
                .stream()
                .filter(post -> postAccessPolicy.canView(post, viewerId))
                .count());
    }

    private GroupPostDTO convertToDTO(Post post, Integer currentUserId) {
        PostEnrichmentContext context = loadEnrichmentContext(List.of(post), currentUserId);
        return convertToDTO(post, currentUserId, context, true);
    }

    private List<GroupPostDTO> convertToDTOs(List<Post> posts, Integer currentUserId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        PostEnrichmentContext context = loadEnrichmentContext(posts, currentUserId);
        return posts.stream()
                .map(post -> convertToDTO(post, currentUserId, context, true))
                .toList();
    }

    private Page<GroupPostDTO> convertToDTOPage(Page<Post> posts, Integer currentUserId) {
        if (posts.isEmpty()) {
            return posts.map(post -> convertToDTO(post, currentUserId));
        }
        PostEnrichmentContext context = loadEnrichmentContext(posts.getContent(), currentUserId);
        return posts.map(post -> convertToDTO(post, currentUserId, context, true));
    }

    private PostEnrichmentContext loadEnrichmentContext(List<Post> posts, Integer currentUserId) {
        List<Post> postsToEnrich = new ArrayList<>(posts);
        posts.stream()
                .map(Post::getOriginalPost)
                .filter(Objects::nonNull)
                .forEach(postsToEnrich::add);

        Set<Integer> postIds = postsToEnrich.stream()
                .map(Post::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Integer> userIds = new LinkedHashSet<>();
        for (Post candidate : postsToEnrich) {
            if (candidate.getAuthor() != null) {
                userIds.add(candidate.getAuthor().getId());
            }
            if (candidate.getApprovedBy() != null) {
                userIds.add(candidate.getApprovedBy().getId());
            }
        }

        Map<Integer, UserProfile> profilesByUserId = userIds.isEmpty()
                ? Map.of()
                : userProfileRepository.findAllByUserIdIn(userIds).stream()
                        .collect(Collectors.toMap(
                                profile -> profile.getUser().getId(),
                                Function.identity(),
                                (first, ignored) -> first));

        Map<Integer, UserAvatar> avatarsByUserId = userIds.isEmpty()
                ? Map.of()
                : userAvatarRepository.findCurrentByUserIds(userIds).stream()
                        .collect(Collectors.toMap(
                                avatar -> avatar.getUser().getId(),
                                Function.identity(),
                                (first, ignored) -> first));

        Map<Integer, List<PostMedia>> mediaByPostId = postIds.isEmpty()
                ? Map.of()
                : postMediaRepository.findAllByPostIdIn(postIds).stream()
                        .collect(Collectors.groupingBy(media -> media.getId().getPostId()));

        Map<Integer, String> reactionByPostId = currentUserId == null || postIds.isEmpty()
                ? Map.of()
                : reactionRepository.findAllByUserIdAndPostIdIn(currentUserId, postIds).stream()
                        .collect(Collectors.toMap(
                                reaction -> reaction.getId().getPostId(),
                                Reaction::getType,
                                (first, ignored) -> first));

        return new PostEnrichmentContext(
                profilesByUserId,
                avatarsByUserId,
                mediaByPostId,
                reactionByPostId);
    }

    private GroupPostDTO convertToDTO(
            Post post,
            Integer currentUserId,
            PostEnrichmentContext context,
            boolean includeOriginalPost) {
        GroupPostDTO dto = new GroupPostDTO();
        dto.setId(post.getId());
        dto.setContent(post.getContent());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setAuthorId(post.getAuthor().getId());
        dto.setAuthorName(post.getAuthor().getUsername());
        dto.setShareCount(post.getShareCount() != null ? post.getShareCount() : 0);

        if (includeOriginalPost && post.getOriginalPost() != null) {
            dto.setOriginalPost(convertToDTO(post.getOriginalPost(), currentUserId, context, false));
        }

        UserProfile authorProfile = context.profilesByUserId().get(post.getAuthor().getId());
        dto.setAuthorFullName(authorProfile != null && authorProfile.getFullName() != null
                ? authorProfile.getFullName()
                : post.getAuthor().getUsername());

        UserAvatar avatar = context.avatarsByUserId().get(post.getAuthor().getId());
        if (avatar != null && avatar.getMedia() != null) {
            dto.setAuthorAvatar(avatar.getMedia().getUrl());
        } else {
            dto.setAuthorAvatar("https://cdn-icons-png.flaticon.com/512/149/149071.png");
        }

        List<PostMedia> mediaList = context.mediaByPostId()
                .getOrDefault(post.getId(), List.of())
                .stream()
                .sorted(Comparator
                        .comparing(pm -> pm.getDisplayOrder() == null ? Integer.MAX_VALUE : pm.getDisplayOrder()))
                .toList();

        List<MediaItem> mediaDto = mediaList.stream().map(pm -> {
            MediaItem item = new MediaItem();
            item.setUrl(pm.getMedia().getUrl());
            item.setType(pm.getMedia().getType());
            item.setDisplayOrder(pm.getDisplayOrder());
            return item;
        }).toList();
        dto.setMedia(mediaDto);
        List<String> images = mediaList.stream()
                .map(pm -> pm.getMedia().getUrl())
                .toList();
        dto.setImages(images);

        // Moderation fields
        dto.setStatus(post.getStatus()); // APPROVED, PENDING, etc.
        dto.setAiStatus(post.getAiStatus());
        dto.setAiScore(post.getAiScore());
        dto.setAiReason(post.getAiReason());
        dto.setVisibility(post.getVisibility());

        if (post.getApprovedBy() != null) {
            UserProfile approverProfile = context.profilesByUserId().get(post.getApprovedBy().getId());
            dto.setApprovedByFullName(approverProfile != null && approverProfile.getFullName() != null
                    ? approverProfile.getFullName()
                    : post.getApprovedBy().getUsername());
        }
        if (currentUserId != null) {
            dto.setCurrentUserReaction(context.reactionByPostId().get(post.getId()));
        }

        if (post.getGroup() != null) {
            dto.setGroupId(post.getGroup().getId());
            dto.setGroupName(post.getGroup().getName());

        }

        // Count
        dto.setReactCount((long) (post.getReactCount() != null ? post.getReactCount() : 0));
        dto.setCommentCount(post.getCommentCount() != null ? post.getCommentCount() : 0);

        dto.setIsPinned(post.getIsPinned());
        dto.setPinnedAt(post.getPinnedAt());

        return dto;
    }

    private record PostEnrichmentContext(
            Map<Integer, UserProfile> profilesByUserId,
            Map<Integer, UserAvatar> avatarsByUserId,
            Map<Integer, List<PostMedia>> mediaByPostId,
            Map<Integer, String> reactionByPostId) {
    }

    @Override
    public Page<GroupPostDTO> getPendingHomepagePosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> posts = postRepository
                .findAllByGroupIdIsNullAndStatusAndIsDeletedFalseOrderByCreatedAtDesc("PENDING", pageable);
        return convertToDTOPage(posts, null);
    }

    @Override
    public Page<GroupPostDTO> getAuditHomepagePosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> posts = postRepository
                .findAllByGroupIdIsNullAndStatusAndAiStatusAndIsDeletedFalseOrderByCreatedAtDesc("APPROVED", "TOXIC",
                        pageable);
        return convertToDTOPage(posts, null);
    }

    @Override
    @Transactional
    @Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void approvePost(Integer postId, Integer adminId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        post.setStatus("APPROVED");
        post.setApprovedBy(admin);
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);

        // Send Notification
        TungNotificationDTO dto = new TungNotificationDTO();
        dto.setContent("Bài viết của bạn đã được phê duyệt.");
        dto.setType("POST_APPROVED");
        dto.setTargetType("POST");
        dto.setTargetId(postId);
        notificationService.sendNotification(dto, post.getAuthor(), admin);

        // Broadcast realtime
        GroupPostDTO postDTO = convertToDTO(post, adminId);
        PostEventDTO event = new PostEventDTO("CREATED", postDTO, post.getId());
        postRealtimeService.publishPostEvent(post, event);
    }

    @Override
    @Transactional
    @Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void rejectPost(Integer postId, Integer adminId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        User author = post.getAuthor();

        post.setStatus("REJECTED");
        post.setApprovedBy(admin);
        postRepository.save(post);

        // Send Notification
        TungNotificationDTO dto = new TungNotificationDTO();
        if (post.getGroup() != null) {
            dto.setTargetType("GROUP");
            dto.setTargetId(post.getGroup().getId());
            dto.setContent("Bài viết của bạn trong nhóm '" + post.getGroup().getName() + "' đã bị từ chối.");
        } else {
            dto.setTargetType("POST");
            dto.setTargetId(post.getId());
            dto.setContent("Bài viết của bạn đã bị từ chối.");
        }
        dto.setType("POST_REJECTED");
        notificationService.sendNotification(dto, author, admin);

        // Broadcast realtime delete
        PostEventDTO event = new PostEventDTO("DELETED", null, post.getId());
        postRealtimeService.publishPostEvent(post, event);

        // Hard delete post record
        postRepository.delete(post);
        postRepository.flush();
    }

    @Override
    @Transactional
    @Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void approveGroupPost(Integer groupId, Integer postId, Integer adminId) {
        requirePostBelongsToGroup(groupId, postId);
        approvePost(postId, adminId);
    }

    @Override
    @Transactional
    @Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void rejectGroupPost(Integer groupId, Integer postId, Integer adminId) {
        requirePostBelongsToGroup(groupId, postId);
        rejectPost(postId, adminId);
    }

    private void requirePostBelongsToGroup(Integer groupId, Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại"));
        if (post.getGroup() == null || !groupId.equals(post.getGroup().getId())) {
            throw new RuntimeException("Bài viết không thuộc nhóm này");
        }
    }

    @Override
    @Transactional
    @Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public Post createPost(CreatePostRequest request, Integer userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = new Post();
        post.setAuthor(author);
        post.setContent(request.getContent());
        post.setVisibility(request.getVisibility() != null ? request.getVisibility() : "PUBLIC");
        post.setCreatedAt(Instant.now());
        post.setUpdatedAt(Instant.now());
        post.setIsDeleted(false);
        post.setCommentCount(0);
        post.setReactCount(0);
        post.setShareCount(0);

        // Set group if provided
        if (request.getGroupId() != null) {
            Group group = groupRepository.findById(request.getGroupId())
                    .orElseThrow(() -> new RuntimeException("Group not found"));

            // SECURITY: Check if user is an ACCEPTED member or owner/admin of the group
            GroupMemberId memberId = new GroupMemberId();
            memberId.setGroupId(group.getId());
            memberId.setUserId(userId);
            boolean isMember = groupMemberRepository.findById(memberId)
                    .map(m -> "ACCEPTED".equals(m.getStatus()))
                    .orElse(false);

            if (!isMember && !group.getOwner().getId().equals(userId)) {
                throw new RuntimeException("Bạn phải tham gia nhóm mới có thể đăng bài.");
            }

            post.setGroup(group);
        }

        // --- ADMIN & OWNER PRIVILEGE ---
        // Website Admins, Group Owners, and Group Admins bypass moderation
        boolean isPrivileged = isPrivilegedUser(author, post.getGroup());

        // MODERATION SCOPE CHECK:
        // 1. Group Posts: Always check (implied Public context)
        // 2. Homepage Posts: Check ONLY if PUBLIC
        boolean isPublic = "PUBLIC".equals(post.getVisibility());
        boolean isGroup = post.getGroup() != null;
        boolean shouldCheckAi = isGroup || isPublic;

        // AI Moderation Logic with Simplified 0.6 threshold
        if (!shouldCheckAi || isPrivileged) {
            post.setStatus("APPROVED");
            post.setAiStatus(isPrivileged ? "SAFE" : "NOT_CHECKED");
            post.setAiScore(0.0);
        } else {
            AiModerationResult aiResult = aiModerationService.checkPostContent(request.getContent());
            post.setCheckedAt(Instant.now());
            post.setAiStatus(aiResult.getLabel());
            post.setAiScore(aiResult.getScore());
            post.setAiReason(aiResult.getReason());

            // Unified threshold: < 0.6 is APPROVED, >= 0.6 is PENDING
            // Note: Fail-Safe logic in AiModerationService returns 0.9 (PENDING) on
            // error/toxic
            if (aiResult.getScore() < 0.6) {
                post.setStatus("APPROVED");
            } else {
                post.setStatus("PENDING");
            }
        }

        Post savedPost = postRepository.save(post);
        attachMediaToPost(savedPost, request.getMediaUrls(), author);

        if ("APPROVED".equals(savedPost.getStatus())) {
            GroupPostDTO dto = convertToDTO(savedPost, null);
            PostEventDTO event = new PostEventDTO("CREATED", dto, savedPost.getId());
            postRealtimeService.publishPostEvent(savedPost, event);
        } else if ("PENDING".equals(savedPost.getStatus())) {
            // Broadcast realtime for admins to see the new pending post
            GroupPostDTO dto = convertToDTO(savedPost, null);
            PostEventDTO event = new PostEventDTO("CREATED", dto, savedPost.getId());
            postRealtimeService.publishPostEvent(savedPost, event);

            TungNotificationDTO notifDto = new TungNotificationDTO();
            notifDto.setContent("Bài viết của bạn đã được gửi và đang chờ quản trị viên phê duyệt.");
            notifDto.setType("POST_PENDING");
            notifDto.setTargetType("POST");
            notifDto.setTargetId(savedPost.getId());
            notificationService.sendNotification(notifDto, author);
        }
        return savedPost;
    }

    @Override
    @Transactional
    public GroupPostDTO createPostAndReturnDTO(CreatePostRequest request, Integer userId) {
        Post savedPost = createPost(request, userId);
        return convertToDTO(savedPost, userId);
    }

    @Override
    @Transactional
    @Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public Post updatePost(Integer postId, org.example.connectcg_be.dto.CreatePostRequest request, Integer userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại"));

        if (!post.getAuthor().getId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa bài viết này");
        }

        // Check if content or visibility changed to re-trigger moderation
        boolean contentChanged = !post.getContent().equals(request.getContent());
        String oldVisibility = post.getVisibility();
        boolean visibilityChanged = !oldVisibility.equals(request.getVisibility());

        post.setContent(request.getContent());
        post.setVisibility(request.getVisibility());
        post.setUpdatedAt(Instant.now());

        // Trigger check if:
        // 1. Content changed
        // 2. Visibility changed to PUBLIC (might have been private/unchecked before)
        // 3. Visibility changed to GROUP context (if we supported moving posts to
        // groups, but here groupId doesn't change on update usually)
        if (contentChanged || (visibilityChanged && "PUBLIC".equals(request.getVisibility()))) {
            boolean isPrivileged = isPrivilegedUser(post.getAuthor(), post.getGroup());

            // MODERATION SCOPE CHECK:
            // 1. Group Posts: Always check (implied Public context)
            // 2. Homepage Posts: Check ONLY if PUBLIC
            boolean isPublic = "PUBLIC".equals(post.getVisibility());
            boolean isGroup = post.getGroup() != null;
            boolean shouldCheckAi = isGroup || isPublic;

            if (isPrivileged || !shouldCheckAi) {
                // Only auto-approve if we are sure it doesn't need checking
                // If it was already PENDING/TOXIC, we should probably keep it?
                // But if scope says "Don't Check", then it is safe to Approve (e.g. became
                // Private)
                post.setStatus("APPROVED");
                post.setAiStatus(isPrivileged ? "SAFE" : "NOT_CHECKED");
                post.setAiScore(0.0);
            } else {
                // Re-trigger AI Moderation
                // Only check if content changed OR if it was previously unchecked/unknown
                // If content IS SAME, and we already checked it (e.g. was Public SAFE, stayed
                // Public), we could skip.
                // But for safety/simplicity, if it became Public, we check.

                // Opt: If content same and already Safe, skip?
                // Let's just check to be safe and simple.
                AiModerationResult aiResult = aiModerationService.checkPostContent(request.getContent());
                post.setCheckedAt(Instant.now());
                post.setAiStatus(aiResult.getLabel());
                post.setAiScore(aiResult.getScore());
                post.setAiReason(aiResult.getReason());

                // Reset approver
                post.setApprovedBy(null);

                // Unified 0.6 threshold
                if (aiResult.getScore() < 0.6) {
                    post.setStatus("APPROVED");
                } else {
                    post.setStatus("PENDING");
                }
            }
        }

        Post savedPost = postRepository.save(post);
        attachMediaToPost(savedPost, request.getMediaUrls(), savedPost.getAuthor());

        if ("APPROVED".equals(savedPost.getStatus())) {
            GroupPostDTO dto = convertToDTO(savedPost, null);
            PostEventDTO event = new PostEventDTO("UPDATED", dto, savedPost.getId());
            postRealtimeService.publishPostEvent(savedPost, event);
        } else if ("PENDING".equals(savedPost.getStatus())) {
            // Broadcast realtime for admins to see the pending update
            GroupPostDTO dto = convertToDTO(savedPost, null);
            PostEventDTO event = new PostEventDTO("UPDATED", dto, savedPost.getId());
            postRealtimeService.publishPostEvent(savedPost, event);

            TungNotificationDTO notifDto = new TungNotificationDTO();
            notifDto.setContent("Bài viết (chỉnh sửa) của bạn đang chờ kiểm duyệt lại.");
            notifDto.setType("POST_PENDING");
            notifDto.setTargetType("POST");
            notifDto.setTargetId(savedPost.getId());
            notificationService.sendNotification(notifDto, savedPost.getAuthor());
        }
        return savedPost;
    }

    @Transactional
    @Override
    public void deletePost(Integer postId, Integer userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại"));

        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

        boolean isAuthor = post.getAuthor().getId().equals(userId);
        boolean isAdmin = "ADMIN".equals(user.getRole());

        if (!isAuthor && !isAdmin) {
            throw new RuntimeException("Bạn không có quyền xóa bài viết này");
        }
        post.setIsDeleted(true);
        postRepository.save(post);

        Post originalPost = post.getOriginalPost();
        if (originalPost != null) {
            long remainingShares = postRepository.countByOriginalPostIdAndIsDeletedFalse(originalPost.getId());
            int shareCount = Math.toIntExact(remainingShares);
            postRepository.updateShareCount(originalPost.getId(), shareCount);
            originalPost.setShareCount(shareCount);

            GroupPostDTO originalDto = convertToDTO(originalPost, userId);
            PostEventDTO shareUpdateEvent = new PostEventDTO("UPDATED", originalDto, originalPost.getId());
            postRealtimeService.publishPostEvent(originalPost, shareUpdateEvent);
        }
        // Broadcast realtime event
        PostEventDTO event = new PostEventDTO("DELETED", null, postId);
        postRealtimeService.publishPostEvent(post, event);
    }

    @Override
    public List<Post> getHomepagePostsByStatus(String status) {
        return postRepository.findAllByGroupIdIsNullAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(status);
    }

    @Override
    public org.springframework.data.domain.Page<GroupPostDTO> getHomepagePostsByStatus(String status, int page,
            int size, Integer currentUserId) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<Post> posts = postRepository
                .findAllByGroupIdIsNullAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(status, pageable);
        return convertToDTOPage(posts, currentUserId);
    }

    private void attachMediaToPost(Post post, List<String> mediaUrls, User uploader) {
        if (mediaUrls == null) {
            return; // không thay đổi media hiện có
        }

        // Xóa liên kết media cũ (nếu có)
        List<PostMedia> existingMedia = postMediaRepository.findAllByPostId(post.getId());
        if (!existingMedia.isEmpty()) {
            postMediaRepository.deleteAll(existingMedia);
        }

        if (mediaUrls.isEmpty()) {
            return; // xóa hết media cũ và không thêm mới
        }

        for (int i = 0; i < mediaUrls.size(); i++) {
            String url = mediaUrls.get(i);
            if (url == null || url.isBlank()) {
                continue;
            }

            Media media = mediaService.resolveOwnedMedia(url, uploader.getId());

            PostMedia postMedia = new PostMedia();
            postMedia.setId(new PostMediaId(post.getId(), media.getId()));
            postMedia.setPost(post);
            postMedia.setMedia(media);
            postMedia.setDisplayOrder(i);
            postMediaRepository.save(postMedia);
        }
    }

    private void cleanupPendingPosts(User author, org.example.connectcg_be.entity.Group group) {
        List<Post> pendingPosts;
        if (group != null) {
            pendingPosts = postRepository.findAllByAuthorIdAndGroupIdAndStatusAndIsDeletedFalse(
                    author.getId(), group.getId(), "PENDING");
        } else {
            pendingPosts = postRepository.findAllByAuthorIdAndStatusAndIsDeletedFalse(
                    author.getId(), "PENDING");
        }

        if (pendingPosts.isEmpty())
            return;

        for (Post p : pendingPosts) {
            // Broadcast realtime delete
            PostEventDTO event = new PostEventDTO("DELETED", null, p.getId());
            postRealtimeService.publishPostEvent(p, event);

            // Hard delete
            postRepository.delete(p);
        }
        postRepository.flush();

        // Send a summary notification
        TungNotificationDTO notifDto = new TungNotificationDTO();
        String groupInfo = (group != null) ? "trong nhóm '" + group.getName() + "' " : "";
        notifDto.setContent(
                "Tất cả các bài viết đang chờ duyệt của bạn " + groupInfo + "đã bị gỡ bỏ do tài khoản bị cấm.");
        notifDto.setType("POST_REJECTED");
        notifDto.setTargetType("USER");
        notifDto.setTargetId(author.getId());
        notificationService.sendNotification(notifDto, author);
    }

    private boolean isPrivilegedUser(User author, Group group) {
        if (author == null)
            return false;

        // Website Admin
        if ("ADMIN".equals(author.getRole()) || "ROLE_ADMIN".equals(author.getRole())) {
            return true;
        }

        // Group Role check
        if (group != null) {
            // Group Owner
            if (group.getOwner() != null && group.getOwner().getId().equals(author.getId())) {
                return true;
            }
            // Group Admin
            GroupMemberId memberId = new GroupMemberId(group.getId(), author.getId());
            return groupMemberRepository.findById(memberId)
                    .map(m -> "ACCEPTED".equals(m.getStatus()) && "ADMIN".equals(m.getRole()))
                    .orElse(false);
        }

        return false;
    }

    @Override
    public GroupPostDTO getPostById(Integer postId, Integer currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại"));
        postAccessPolicy.requireCanView(post, currentUserId);
        return convertToDTO(post, currentUserId);
    }

    @Override
    @Transactional
    public void togglePinPost(Integer postId, Integer userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại"));

        if (post.getGroup() == null) {
            throw new RuntimeException("Chỉ có thể ghim bài viết trong nhóm");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

        // Check if user is Admin or Group Owner/Admin
        boolean isPrivileged = isPrivilegedUser(user, post.getGroup());
        if (!isPrivileged) {
            throw new RuntimeException("Bạn không có quyền ghim bài viết này");
        }

        boolean currentPinned = post.getIsPinned() != null && post.getIsPinned();
        post.setIsPinned(!currentPinned);
        post.setPinnedAt(!currentPinned ? Instant.now() : null);
        postRepository.save(post);

        // Broadcast realtime update
        GroupPostDTO postDTO = convertToDTO(post, userId);
        PostEventDTO event = new PostEventDTO("UPDATED", postDTO, post.getId());
        postRealtimeService.publishPostEvent(post, event);
    }

    @Override
    public GroupPostDTO sharePost(Integer originalPostId, CreatePostRequest request, Integer userId) {
        Post originalPost = postRepository.findById(originalPostId)
                .orElseThrow(() -> new RuntimeException("Bài viết gốc không tồn tại"));
        postAccessPolicy.requireCanView(originalPost, userId);

        // Nếu bài viết này là bài share, thì lấy bài gốc thực sự (root post) của nó.
        // Luôn đi tìm bài viết gốc cuối cùng để bài share luôn được gắn vào bài gốc
        // thật sự.
        while (originalPost.getOriginalPost() != null) {
            originalPost = originalPost.getOriginalPost();
        }
        postAccessPolicy.requireCanView(originalPost, userId);

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng này"));

        Post newPost = new Post();
        newPost.setAuthor(author);
        newPost.setContent(request.getContent()); // Caption của người share
        newPost.setOriginalPost(originalPost); // Link tới bài gốc
        newPost.setVisibility(request.getVisibility() != null ? request.getVisibility() : "PUBLIC");
        newPost.setCreatedAt(Instant.now());
        newPost.setUpdatedAt(Instant.now());
        newPost.setIsDeleted(false);
        newPost.setReactCount(0);
        newPost.setCommentCount(0);
        newPost.setShareCount(0);

        if (request.getGroupId() != null) {
            Group group = groupRepository.findById(request.getGroupId())
                    .orElseThrow(() -> new RuntimeException("Group not found"));

            // SECURITY: Check if user is an ACCEPTED member or owner/admin of the group
            GroupMemberId memberId = new GroupMemberId();
            memberId.setGroupId(group.getId());
            memberId.setUserId(userId);
            boolean isMember = groupMemberRepository.findById(memberId)
                    .map(m -> "ACCEPTED".equals(m.getStatus()))
                    .orElse(false);

            if (!isMember && !group.getOwner().getId().equals(userId)) {
                throw new RuntimeException("Bạn phải tham gia nhóm mới có thể đăng bài.");
            }

            newPost.setGroup(group);
        }

        AiModerationResult aiResult = aiModerationService.checkPostContent(request.getContent());
        newPost.setCheckedAt(Instant.now());
        newPost.setAiStatus(aiResult.getLabel());
        newPost.setAiScore(aiResult.getScore());
        newPost.setAiReason(aiResult.getReason());

        // Unified threshold: < 0.6 is APPROVED, >= 0.6 is PENDING
        // Note: Fail-Safe logic in AiModerationService returns 0.9 (PENDING) on
        // error/toxic
        if (aiResult.getScore() < 0.6) {
            newPost.setStatus("APPROVED");
        } else {
            newPost.setStatus("PENDING");
        }

        Post savedPost = postRepository.save(newPost);

        int shareCount = Math.toIntExact(
                postRepository.countByOriginalPostIdAndIsDeletedFalse(originalPost.getId()));
        postRepository.updateShareCount(originalPost.getId(), shareCount);
        originalPost.setShareCount(shareCount);

        // Broadcast realtime update cho bài gốc (để cập nhật lượt share)
        GroupPostDTO originalDto = convertToDTO(originalPost, userId);
        PostEventDTO shareUpdateEvent = new PostEventDTO("UPDATED", originalDto, originalPost.getId());
        postRealtimeService.publishPostEvent(originalPost, shareUpdateEvent);

        if (!originalPost.getAuthor().getId().equals(userId)) {
            TungNotificationDTO notif = new TungNotificationDTO();
            notif.setContent("đã chia sẻ bài viết của bạn.");
            notif.setType("POST_SHARED");
            notif.setTargetType("POST");
            notif.setTargetId(newPost.getId());
            try {
                notificationService.sendNotification(notif, author, originalPost.getAuthor()); // From, To
            } catch (Exception e) {
                // ignore notification error
            }
        }
        return convertToDTO(savedPost, userId);
    }
}
