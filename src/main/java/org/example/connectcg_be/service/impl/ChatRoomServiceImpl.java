package org.example.connectcg_be.service.impl;

import org.example.connectcg_be.dto.ChatMemberDTO;
import org.example.connectcg_be.dto.ChatRoomDTO;
import org.example.connectcg_be.dto.ReadReceiptDTO;
import org.example.connectcg_be.entity.*;
import org.example.connectcg_be.repository.*;
import org.example.connectcg_be.service.ChatRoomService;
import org.example.connectcg_be.realtime.RealtimeEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collection;
import java.util.function.Function;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatRoomServiceImpl implements ChatRoomService {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserAvatarRepository userAvatarRepository;

    @Autowired
    private org.example.connectcg_be.service.MediaService mediaService;

    @Autowired
    private RealtimeEventPublisher realtimeEventPublisher;

    @Override
    @Transactional
    public ChatRoomDTO getOrCreateDirectChat(User user1, User user2) {
        // Tìm phòng chat chung giữa 2 người
        List<ChatRoomMember> memberships1 = chatRoomMemberRepository.findByUser_Id(user1.getId());
        for (ChatRoomMember m1 : memberships1) {
            ChatRoom room = m1.getChatRoom();
            if ("DIRECT".equals(room.getType())) {
                Optional<ChatRoomMember> m2 = chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(room.getId(),
                        user2.getId());
                if (m2.isPresent()) {
                    return convertToDTO(room, user1.getId());
                }
            }
        }

        // Nếu chưa có, tạo mới
        ChatRoom room = new ChatRoom();
        room.setType("DIRECT");
        room.setFirebaseRoomKey(UUID.randomUUID().toString());
        room.setCreatedBy(user1);
        room.setCreatedAt(Instant.now());
        room.setIsActive(true);
        room = chatRoomRepository.save(room);

        // Add members
        addMember(room, user1, "ADMIN");
        addMember(room, user2, "MEMBER");

        return convertToDTO(room, user1.getId());
    }

    @Override
    @Transactional
    public ChatRoomDTO createGroupChat(User creator, String name, List<User> members) {
        ChatRoom room = new ChatRoom();
        room.setType("GROUP");
        room.setName(name);
        room.setFirebaseRoomKey(UUID.randomUUID().toString());
        room.setCreatedBy(creator);
        room.setCreatedAt(Instant.now());
        room.setIsActive(true);
        room = chatRoomRepository.save(room);

        addMember(room, creator, "ADMIN");
        for (User member : members) {
            if (!member.getId().equals(creator.getId())) {
                addMember(room, member, "MEMBER");
            }
        }

        return convertToDTO(room, creator.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomDTO> getUserChatRooms(Integer userId) {
        List<ChatRoom> rooms = chatRoomMemberRepository.findByUser_IdOrderByLastMessageAtDesc(userId).stream()
                .map(ChatRoomMember::getChatRoom)
                .collect(Collectors.toList());
        return convertToDTOs(rooms, userId);
    }

    @Override
    @Transactional
    public void updateLastMessageAt(String firebaseRoomKey, User sender) {
        chatRoomRepository.findByFirebaseRoomKey(firebaseRoomKey).ifPresent(room -> {
            room.setLastMessageAt(Instant.now());
            chatRoomRepository.save(room);

            // Tự động đánh dấu là đã đọc cho người gửi
            if (sender != null) {
                chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(room.getId(), sender.getId())
                        .ifPresent(member -> {
                            member.setLastReadAt(Instant.now());
                            chatRoomMemberRepository.save(member);
                        });
            }

            // Gửi thông báo WebSocket cho tất cả thành viên trong nhóm
            List<ChatRoomMember> members = chatRoomMemberRepository.findByChatRoom_Id(room.getId());
            for (ChatRoomMember member : members) {
                // Calculate unread status for this specific member
                int unreadCount = 0;
                if (sender == null || !member.getUser().getId().equals(sender.getId())) {
                    // It's a new message for this member
                    unreadCount = 1;
                }

                realtimeEventPublisher.sendToUser(
                        member.getUser().getUsername(),
                        "/queue/chat",
                        Map.of(
                                "type", "CHAT_UPDATE",
                                "roomId", room.getId(),
                                "data", Map.of(
                                        "firebaseRoomKey", room.getFirebaseRoomKey(),
                                        "lastMessageAt", room.getLastMessageAt(),
                                        "unreadCount", unreadCount)));
            }
        });
    }

    @Override
    @Transactional
    public ChatRoomDTO renameRoom(Long roomId, String newName, User currentUser) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        ChatRoomMember membership = chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(roomId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));

        if (!"GROUP".equals(room.getType()) || !"ADMIN".equals(membership.getRole())) {
            throw new AccessDeniedException("Only admins can rename group chats");
        }

        room.setName(newName);
        room = chatRoomRepository.save(room);

        // Broadcast CHAT_UPDATE to all members
        List<ChatRoomMember> members = chatRoomMemberRepository.findByChatRoom_Id(roomId);
        for (ChatRoomMember m : members) {
            realtimeEventPublisher.sendToUser(
                    m.getUser().getUsername(),
                    "/queue/chat",
                    Map.of(
                            "type", "CHAT_UPDATE",
                            "roomId", roomId,
                            "data", Map.of("name", newName)));
        }

        return convertToDTO(room, currentUser.getId());
    }

    @Override
    @Transactional
    public ChatRoomDTO updateAvatar(Long roomId, String avatarUrl, User currentUser) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        ChatRoomMember membership = chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(roomId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));

        if (!"GROUP".equals(room.getType()) || !"ADMIN".equals(membership.getRole())) {
            throw new AccessDeniedException("Only admins can change group avatar");
        }

        mediaService.resolveOwnedMedia(avatarUrl, currentUser.getId());
        room.setAvatarUrl(avatarUrl);
        room = chatRoomRepository.save(room);

        // Broadcast CHAT_UPDATE to all members
        List<ChatRoomMember> members = chatRoomMemberRepository.findByChatRoom_Id(roomId);
        for (ChatRoomMember m : members) {
            realtimeEventPublisher.sendToUser(
                    m.getUser().getUsername(),
                    "/queue/chat",
                    Map.of(
                            "type", "CHAT_UPDATE",
                            "roomId", roomId,
                            "data", Map.of("avatarUrl", avatarUrl)));
        }

        return convertToDTO(room, currentUser.getId());
    }

    @Override
    @Transactional
    public ChatRoomDTO inviteMembers(Long roomId, List<Integer> invitedUserIds, User currentUser) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!"GROUP".equals(room.getType())) {
            throw new RuntimeException("Cannot invite users to direct chat");
        }

        // Check current user is member
        chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(roomId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));

        // Get current member IDs to check duplicates
        List<Integer> currentMemberIds = chatRoomMemberRepository.findByChatRoom_Id(roomId)
                .stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toList());

        // Process each invited user
        for (Integer invitedUserId : invitedUserIds) {
            // Skip if already a member
            if (currentMemberIds.contains(invitedUserId)) {
                continue;
            }

            User invitedUser = userRepository.findById(invitedUserId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + invitedUserId));

            // Add member
            addMember(room, invitedUser, "MEMBER");
        }

        // Broadcast CHAT_UPDATE to ALL members (newly added and existing)
        List<ChatRoomMember> updatedMembers = chatRoomMemberRepository.findByChatRoom_Id(roomId);
        for (ChatRoomMember m : updatedMembers) {
            ChatRoomDTO roomDto = convertToDTO(room, m.getUser().getId());
            realtimeEventPublisher.sendToUser(
                    m.getUser().getUsername(),
                    "/queue/chat",
                    Map.of(
                            "type", "CHAT_UPDATE",
                            "roomId", roomId,
                            "data", roomDto));
        }

        return convertToDTO(room, currentUser.getId());
    }

    @Override
    public ChatRoomDTO convertToDTO(ChatRoom room, Integer currentUserId) {
        List<ChatRoomMember> roomMembers = chatRoomMemberRepository.findByChatRoom_Id(room.getId());
        MemberData memberData = loadMemberData(roomMembers);
        return convertToDTO(room, currentUserId, roomMembers, memberData);
    }

    private List<ChatRoomDTO> convertToDTOs(List<ChatRoom> rooms, Integer currentUserId) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        List<ChatRoomMember> allMembers = chatRoomMemberRepository.findByChatRoom_IdIn(roomIds);
        Map<Long, List<ChatRoomMember>> membersByRoom = allMembers.stream()
                .collect(Collectors.groupingBy(member -> member.getChatRoom().getId()));
        MemberData memberData = loadMemberData(allMembers);
        return rooms.stream()
                .map(room -> convertToDTO(
                        room,
                        currentUserId,
                        membersByRoom.getOrDefault(room.getId(), List.of()),
                        memberData))
                .toList();
    }

    private MemberData loadMemberData(Collection<ChatRoomMember> members) {
        List<Integer> userIds = members.stream()
                .map(member -> member.getUser().getId())
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return new MemberData(Map.of(), Map.of());
        }
        Map<Integer, UserProfile> profiles = userProfileRepository.findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity()));
        Map<Integer, UserAvatar> avatars = userAvatarRepository.findCurrentByUserIds(userIds).stream()
                .collect(Collectors.toMap(avatar -> avatar.getUser().getId(), Function.identity()));
        return new MemberData(profiles, avatars);
    }

    private ChatRoomDTO convertToDTO(
            ChatRoom room,
            Integer currentUserId,
            List<ChatRoomMember> roomMembers,
            MemberData memberData) {
        String name = room.getName();
        String avatarUrl = room.getAvatarUrl();
        Integer otherParticipantId = null;

        List<ChatMemberDTO> memberDTOs = roomMembers.stream().map(rm -> {
            Integer uid = rm.getUser().getId();
            String uName = rm.getUser().getUsername();
            UserProfile profile = memberData.profiles().get(uid);
            String fName = profile != null ? profile.getFullName() : uName;

            String aUrl = null;
            UserAvatar avatar = memberData.avatars().get(uid);
            if (avatar != null && avatar.getMedia() != null) {
                aUrl = avatar.getMedia().getUrl();
            }

            return ChatMemberDTO.builder()
                    .id(uid)
                    .fullName(fName)
                    .avatarUrl(aUrl)
                    .role(rm.getRole())
                    .lastReadAt(rm.getLastReadAt())
                    .build();
        }).collect(Collectors.toList());

        if ("DIRECT".equals(room.getType())) {
            // Find the other member specifically for the room header info
            for (ChatMemberDTO m : memberDTOs) {
                if (!m.getId().equals(currentUserId)) {
                    otherParticipantId = m.getId();
                    name = m.getFullName();
                    avatarUrl = m.getAvatarUrl();
                    break;
                }
            }
        }

        ChatRoomDTO dto = ChatRoomDTO.builder()
                .id(room.getId())
                .type(room.getType())
                .name(name)
                .avatarUrl(avatarUrl)
                .firebaseRoomKey(room.getFirebaseRoomKey())
                .otherParticipantId(otherParticipantId)
                .members(memberDTOs)
                .lastMessageAt(room.getLastMessageAt())
                .createdAt(room.getCreatedAt())
                .build();

        // Calculate unread count (simplified: 1 if unread, 0 if read)
        ChatRoomMember currentMember = roomMembers.stream()
                .filter(member -> member.getUser().getId().equals(currentUserId))
                .findFirst()
                .orElse(null);
        if (currentMember != null) {
            dto.setClientClearedAt(currentMember.getClientClearedAt());
            dto.setCurrentUserRole(currentMember.getRole());

            if (room.getLastMessageAt() != null) {
                Instant lastRead = currentMember.getLastReadAt();
                Instant clearedAt = currentMember.getClientClearedAt();

                // If last message is newer than last read AND newer than clear time -> unread
                boolean isNewerThanRead = lastRead == null || room.getLastMessageAt().isAfter(lastRead);
                boolean isNewerThanClear = clearedAt == null || room.getLastMessageAt().isAfter(clearedAt);

                if (isNewerThanRead && isNewerThanClear) {
                    dto.setUnreadCount(1);
                } else {
                    dto.setUnreadCount(0);
                }
            } else {
                dto.setUnreadCount(0);
            }
        } else {
            dto.setUnreadCount(0);
        }

        return dto;
    }

    private record MemberData(
            Map<Integer, UserProfile> profiles,
            Map<Integer, UserAvatar> avatars) {
    }

    @Override
    @Transactional
    public void markAsRead(Long roomId, User currentUser) {
        ChatRoomMember member = chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(roomId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));

        Instant now = Instant.now();
        member.setLastReadAt(now);
        chatRoomMemberRepository.save(member);

        // Broadcast READ_RECEIPT signal to the room's topic
        ReadReceiptDTO receipt = ReadReceiptDTO.builder()
                .roomId(roomId)
                .firebaseRoomKey(member.getChatRoom().getFirebaseRoomKey())
                .userId(currentUser.getId())
                .lastReadAt(now)
                .build();

        realtimeEventPublisher.sendToTopic(
                "/topic/chat/" + receipt.getFirebaseRoomKey() + "/seen",
                receipt);

        // Also notify the current user to update their sidebar (unreadCount = 0)
        ChatRoomDTO roomDto = convertToDTO(member.getChatRoom(), currentUser.getId());
        realtimeEventPublisher.sendToUser(
                currentUser.getUsername(),
                "/queue/chat",
                Map.of(
                        "type", "CHAT_UPDATE",
                        "roomId", roomId,
                        "data", roomDto));
    }

    @Override
    @Transactional
    public void clearHistory(Long roomId, User currentUser) {
        ChatRoomMember member = chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(roomId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));
        member.setClientClearedAt(Instant.now());
        chatRoomMemberRepository.save(member);
    }

    @Override
    @Transactional
    public void deleteChatRoom(Long roomId, User currentUser) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        ChatRoomMember membership = chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(roomId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));

        if ("GROUP".equals(room.getType())) {
            // Chỉ ADMIN mới được xóa nhóm
            if (!"ADMIN".equals(membership.getRole())) {
                throw new RuntimeException("Only group admins can delete the room");
            }
        } else {
            // Với DIRECT chat, bất kỳ thành viên nào cũng có quyền xóa (xóa chung cho cả 2)
            // Membership check đã ở trên rồi
        }

        // Broadcast CHAT_REMOVE to everyone before deleting
        List<ChatRoomMember> membersToDelete = chatRoomMemberRepository.findByChatRoom_Id(roomId);
        for (ChatRoomMember m : membersToDelete) {
            realtimeEventPublisher.sendToUser(
                    m.getUser().getUsername(),
                    "/queue/chat",
                    Map.of(
                            "type", "CHAT_REMOVE",
                            "roomId", roomId,
                            "reason", "DELETED"));
        }

        // 1. Xóa tất cả thành viên
        chatRoomMemberRepository.deleteByChatRoom_Id(roomId);

        // 2. Xóa phòng chat
        chatRoomRepository.delete(room);
    }

    @Override
    @Transactional
    public ChatRoomDTO removeMember(Long roomId, Integer userIdToRemove, User currentUser) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!"GROUP".equals(room.getType())) {
            throw new RuntimeException("Cannot remove members from direct chat");
        }

        // Check if current user is ADMIN of the room
        ChatRoomMember adminMembership = chatRoomMemberRepository
                .findByChatRoom_IdAndUser_Id(roomId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));

        if (!"ADMIN".equals(adminMembership.getRole())) {
            throw new RuntimeException("Only admins can remove members");
        }

        // Cannot remove self (use Leave/Delete Group instead)
        if (currentUser.getId().equals(userIdToRemove)) {
            throw new RuntimeException("Cannot kick yourself. Use 'Leave Group' or 'Delete Group' instead.");
        }

        // Check if target user is a member
        ChatRoomMember targetMembership = chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(roomId, userIdToRemove)
                .orElseThrow(() -> new RuntimeException("User is not a member of this room"));

        // Remove the member
        chatRoomMemberRepository.delete(targetMembership);

        // Notify the kicked user to remove sidebar item
        User targetUser = userRepository.findById(userIdToRemove).orElse(null);
        if (targetUser != null) {
            realtimeEventPublisher.sendToUser(
                    targetUser.getUsername(),
                    "/queue/chat",
                    Map.of(
                            "type", "CHAT_REMOVE",
                            "roomId", roomId,
                            "reason", "KICKED"));
        }

        // Notify remaining members to update their member list
        List<ChatRoomMember> remainingMembers = chatRoomMemberRepository.findByChatRoom_Id(roomId);
        for (ChatRoomMember m : remainingMembers) {
            ChatRoomDTO roomDto = convertToDTO(room, m.getUser().getId());
            realtimeEventPublisher.sendToUser(
                    m.getUser().getUsername(),
                    "/queue/chat",
                    Map.of(
                            "type", "CHAT_UPDATE",
                            "roomId", roomId,
                            "data", roomDto));
        }

        return convertToDTO(room, currentUser.getId());
    }

    @Override
    @Transactional
    public void leaveChatRoom(Long roomId, User currentUser) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!"GROUP".equals(room.getType())) {
            throw new RuntimeException("Cannot leave direct chat. Delete the chat instead.");
        }

        ChatRoomMember member = chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(roomId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));

        // Check if current user is ADMIN of the room
        if ("ADMIN".equals(member.getRole())) {
            List<ChatRoomMember> allMembers = chatRoomMemberRepository.findByChatRoom_Id(roomId);
            long adminCount = allMembers.stream().filter(m -> "ADMIN".equals(m.getRole())).count();
            if (adminCount <= 1) {
                throw new RuntimeException(
                        "You are the only Admin. Please assign another Admin or dissolve the group.");
            }
        }

        // Delete membership
        chatRoomMemberRepository.delete(member);

        // Notify the leaving user to remove from sidebar
        realtimeEventPublisher.sendToUser(
                currentUser.getUsername(),
                "/queue/chat",
                Map.of(
                        "type", "CHAT_REMOVE",
                        "roomId", roomId,
                        "reason", "LEFT"));

        // Check if room is empty
        List<ChatRoomMember> remaining = chatRoomMemberRepository.findByChatRoom_Id(roomId);
        if (remaining.isEmpty()) {
            chatRoomRepository.delete(room);
        } else {
            // Notify remaining members that someone left
            for (ChatRoomMember m : remaining) {
                ChatRoomDTO roomDto = convertToDTO(room, m.getUser().getId());
                realtimeEventPublisher.sendToUser(
                        m.getUser().getUsername(),
                        "/queue/chat",
                        Map.of(
                                "type", "CHAT_UPDATE",
                                "roomId", roomId,
                                "data", roomDto));
            }
        }
    }

    private void addMember(ChatRoom room, User user, String role) {
        ChatRoomMember member = new ChatRoomMember();
        ChatRoomMemberId id = new ChatRoomMemberId();
        id.setChatRoomId(room.getId());
        id.setUserId(user.getId());
        member.setId(id);
        member.setChatRoom(room);
        member.setUser(user);
        member.setRole(role);
        member.setJoinedAt(Instant.now());
        chatRoomMemberRepository.save(member);
    }
}
