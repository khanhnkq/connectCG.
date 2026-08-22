package org.example.connectcg_be.service;

import lombok.RequiredArgsConstructor;
import org.example.connectcg_be.entity.Group;
import org.example.connectcg_be.entity.GroupMember;
import org.example.connectcg_be.entity.GroupMemberId;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.FriendRepository;
import org.example.connectcg_be.repository.GroupMemberRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostAccessPolicy {
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final GroupMemberRepository groupMemberRepository;

    public void requireCanView(Post post, Integer viewerId) {
        if (!canView(post, viewerId)) {
            throw new AccessDeniedException("Bạn không có quyền xem bài viết này");
        }
    }

    public boolean canView(Post post, Integer viewerId) {
        if (post == null || viewerId == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            return false;
        }

        User viewer = userRepository.findById(viewerId).orElse(null);
        if (viewer == null || Boolean.TRUE.equals(viewer.getIsDeleted()) || Boolean.TRUE.equals(viewer.getIsLocked())) {
            return false;
        }

        boolean isSystemAdmin = "ADMIN".equals(viewer.getRole());
        boolean isAuthor = post.getAuthor() != null && viewerId.equals(post.getAuthor().getId());

        if (!"APPROVED".equals(post.getStatus())) {
            return isSystemAdmin || isAuthor || isActiveGroupAdmin(post.getGroup(), viewerId);
        }

        if (post.getGroup() != null) {
            return canViewGroupPost(post.getGroup(), viewerId, isSystemAdmin);
        }

        if (isSystemAdmin || isAuthor || "PUBLIC".equals(post.getVisibility())) {
            return true;
        }
        if (!"FRIENDS".equals(post.getVisibility())) {
            return false;
        }
        Integer authorId = post.getAuthor().getId();
        return friendRepository.existsByUserIdAndFriendId(viewerId, authorId)
                || friendRepository.existsByUserIdAndFriendId(authorId, viewerId);
    }

    private boolean canViewGroupPost(Group group, Integer viewerId, boolean isSystemAdmin) {
        if (Boolean.TRUE.equals(group.getIsDeleted())) {
            return false;
        }
        if (isSystemAdmin || group.getOwner().getId().equals(viewerId)) {
            return true;
        }

        Optional<GroupMember> membership = groupMemberRepository.findById(new GroupMemberId(group.getId(), viewerId));
        if (membership.map(member -> "BANNED".equals(member.getStatus())).orElse(false)) {
            return false;
        }
        if ("PUBLIC".equals(group.getPrivacy())) {
            return true;
        }
        return membership.map(member -> "ACCEPTED".equals(member.getStatus())).orElse(false);
    }

    private boolean isActiveGroupAdmin(Group group, Integer viewerId) {
        if (group == null || Boolean.TRUE.equals(group.getIsDeleted())) {
            return false;
        }
        if (group.getOwner().getId().equals(viewerId)) {
            return true;
        }
        return groupMemberRepository.findById(new GroupMemberId(group.getId(), viewerId))
                .map(member -> "ACCEPTED".equals(member.getStatus()) && "ADMIN".equals(member.getRole()))
                .orElse(false);
    }
}
