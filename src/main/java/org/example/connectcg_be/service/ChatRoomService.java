package org.example.connectcg_be.service;

import org.example.connectcg_be.entity.ChatRoom;
import org.example.connectcg_be.entity.User;

import java.util.List;
import java.util.Optional;

public interface ChatRoomService {
    ChatRoom getOrCreateDirectChat(User user1, User user2);

    ChatRoom createGroupChat(User creator, String name, List<User> members);

    Optional<ChatRoom> findByFirebaseKey(String key);

    List<ChatRoom> getUserChatRooms(Integer userId);

    ChatRoom renameRoom(Long roomId, String newName, User currentUser);

    ChatRoom updateAvatar(Long roomId, String avatarUrl, User currentUser);
}
