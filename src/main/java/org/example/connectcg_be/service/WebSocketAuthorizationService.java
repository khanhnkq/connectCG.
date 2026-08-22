package org.example.connectcg_be.service;

import lombok.RequiredArgsConstructor;
import org.example.connectcg_be.entity.ChatRoom;
import org.example.connectcg_be.entity.Group;
import org.example.connectcg_be.entity.GroupMember;
import org.example.connectcg_be.entity.GroupMemberId;
import org.example.connectcg_be.repository.ChatRoomMemberRepository;
import org.example.connectcg_be.repository.ChatRoomRepository;
import org.example.connectcg_be.repository.GroupMemberRepository;
import org.example.connectcg_be.repository.GroupRepository;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WebSocketAuthorizationService {
    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chat/([^/]+)/(typing|seen)$");
    private static final Pattern POST_TOPIC = Pattern.compile("^/topic/posts/(\\d+)/(updates|comments|reactions)$");
    private static final Pattern GROUP_TOPIC = Pattern.compile("^/topic/groups/(\\d+)/(posts|membership)(/pending)?$");
    private static final Set<String> SHARED_TOPICS = Set.of(
            "/topic/public/status",
            "/topic/posts",
            "/topic/users");

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final PostRepository postRepository;
    private final PostAccessPolicy postAccessPolicy;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public boolean canSubscribe(Integer userId, String destination) {
        if (destination == null) {
            return false;
        }
        if (destination.startsWith("/user/queue/")) {
            return true;
        }
        Matcher matcher = CHAT_TOPIC.matcher(destination);
        if (matcher.matches()) {
            return canAccessChat(userId, matcher.group(1));
        }
        matcher = POST_TOPIC.matcher(destination);
        if (matcher.matches()) {
            Integer postId = Integer.valueOf(matcher.group(1));
            return postRepository.findById(postId)
                    .map(post -> postAccessPolicy.canView(post, userId))
                    .orElse(false);
        }
        matcher = GROUP_TOPIC.matcher(destination);
        if (matcher.matches()) {
            Integer groupId = Integer.valueOf(matcher.group(1));
            boolean pendingPosts = matcher.group(3) != null;
            return pendingPosts ? canAdminGroup(userId, groupId) : canViewGroup(userId, groupId);
        }
        return SHARED_TOPICS.contains(destination);
    }

    public boolean canSend(String destination) {
        return "/app/chat/typing".equals(destination);
    }

    public boolean canAccessChat(Integer userId, String firebaseRoomKey) {
        return chatRoomRepository.findByFirebaseRoomKey(firebaseRoomKey)
                .map(ChatRoom::getId)
                .map(roomId -> chatRoomMemberRepository.existsByChatRoom_IdAndUser_Id(roomId, userId))
                .orElse(false);
    }

    private boolean canViewGroup(Integer userId, Integer groupId) {
        Group group = groupRepository.findByIdAndIsDeletedFalse(groupId).orElse(null);
        if (group == null) {
            return false;
        }
        if (isSystemAdmin(userId) || group.getOwner().getId().equals(userId)) {
            return true;
        }
        Optional<GroupMember> member = groupMemberRepository.findById(new GroupMemberId(groupId, userId));
        if (member.map(value -> "BANNED".equals(value.getStatus())).orElse(false)) {
            return false;
        }
        return "PUBLIC".equals(group.getPrivacy())
                || member.map(value -> "ACCEPTED".equals(value.getStatus())).orElse(false);
    }

    private boolean canAdminGroup(Integer userId, Integer groupId) {
        Group group = groupRepository.findByIdAndIsDeletedFalse(groupId).orElse(null);
        if (group == null) {
            return false;
        }
        if (isSystemAdmin(userId) || group.getOwner().getId().equals(userId)) {
            return true;
        }
        return groupMemberRepository.findById(new GroupMemberId(groupId, userId))
                .map(member -> "ACCEPTED".equals(member.getStatus()) && "ADMIN".equals(member.getRole()))
                .orElse(false);
    }

    private boolean isSystemAdmin(Integer userId) {
        return userRepository.findById(userId)
                .map(user -> "ADMIN".equals(user.getRole()))
                .orElse(false);
    }
}
