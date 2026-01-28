package org.example.connectcg_be.service.impl;

import org.example.connectcg_be.entity.*;
import org.example.connectcg_be.repository.ChatRoomMemberRepository;
import org.example.connectcg_be.repository.ChatRoomRepository;
import org.example.connectcg_be.service.ChatRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatRoomServiceImpl implements ChatRoomService {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Override
    @Transactional
    public ChatRoom getOrCreateDirectChat(User user1, User user2) {
        // Tìm phòng chat chung giữa 2 người
        List<ChatRoomMember> memberships1 = chatRoomMemberRepository.findByUser_Id(user1.getId());
        for (ChatRoomMember m1 : memberships1) {
            ChatRoom room = m1.getChatRoom();
            if ("DIRECT".equals(room.getType())) {
                Optional<ChatRoomMember> m2 = chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(room.getId(),
                        user2.getId());
                if (m2.isPresent()) {
                    return room;
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

        return room;
    }

    @Override
    @Transactional
    public ChatRoom createGroupChat(User creator, String name, List<User> members) {
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

        return room;
    }

    @Override
    public Optional<ChatRoom> findByFirebaseKey(String key) {
        return chatRoomRepository.findByFirebaseRoomKey(key);
    }

    @Override
    public List<ChatRoom> getUserChatRooms(Integer userId) {
        return chatRoomMemberRepository.findByUser_Id(userId).stream()
                .map(ChatRoomMember::getChatRoom)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public ChatRoom renameRoom(Long roomId, String newName, User currentUser) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        ChatRoomMember membership = chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(roomId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));

        if (!"ADMIN".equals(membership.getRole()) && !"GROUP".equals(room.getType())) {
            throw new RuntimeException("Only admins can rename group chats");
        }

        room.setName(newName);
        return chatRoomRepository.save(room);
    }

    @Override
    @Transactional // Implementation of ChatRoomService.updateAvatar
    public ChatRoom updateAvatar(Long roomId, String avatarUrl, User currentUser) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        ChatRoomMember membership = chatRoomMemberRepository.findByChatRoom_IdAndUser_Id(roomId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));

        if (!"ADMIN".equals(membership.getRole()) && !"GROUP".equals(room.getType())) {
            throw new RuntimeException("Only admins can change group avatar");
        }

        room.setAvatarUrl(avatarUrl);
        return chatRoomRepository.save(room);
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
