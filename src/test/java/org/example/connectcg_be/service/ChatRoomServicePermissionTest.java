package org.example.connectcg_be.service;

import org.example.connectcg_be.entity.ChatRoom;
import org.example.connectcg_be.entity.ChatRoomMember;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.ChatRoomMemberRepository;
import org.example.connectcg_be.repository.ChatRoomRepository;
import org.example.connectcg_be.service.impl.ChatRoomServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatRoomServicePermissionTest {
    private final ChatRoomRepository roomRepository = mock(ChatRoomRepository.class);
    private final ChatRoomMemberRepository memberRepository = mock(ChatRoomMemberRepository.class);
    private final ChatRoomServiceImpl service = createService();

    @Test
    void regularGroupMemberCannotRenameRoom() {
        User user = user(7);
        stubGroupMembership(user, "MEMBER");

        assertThrows(AccessDeniedException.class, () -> service.renameRoom(11L, "new name", user));
    }

    @Test
    void regularGroupMemberCannotChangeRoomAvatar() {
        User user = user(7);
        stubGroupMembership(user, "MEMBER");

        assertThrows(AccessDeniedException.class, () -> service.updateAvatar(11L, "avatar-url", user));
    }

    private ChatRoomServiceImpl createService() {
        ChatRoomServiceImpl chatRoomService = new ChatRoomServiceImpl();
        ReflectionTestUtils.setField(chatRoomService, "chatRoomRepository", roomRepository);
        ReflectionTestUtils.setField(chatRoomService, "chatRoomMemberRepository", memberRepository);
        return chatRoomService;
    }

    private void stubGroupMembership(User user, String role) {
        ChatRoom room = new ChatRoom();
        room.setId(11L);
        room.setType("GROUP");
        ChatRoomMember membership = new ChatRoomMember();
        membership.setRole(role);
        when(roomRepository.findById(11L)).thenReturn(Optional.of(room));
        when(memberRepository.findByChatRoom_IdAndUser_Id(11L, user.getId()))
                .thenReturn(Optional.of(membership));
    }

    private User user(Integer id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
