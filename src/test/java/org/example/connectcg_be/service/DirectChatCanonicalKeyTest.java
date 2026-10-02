package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.ChatRoomDTO;
import org.example.connectcg_be.entity.ChatRoom;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.ChatRoomMemberRepository;
import org.example.connectcg_be.repository.ChatRoomRepository;
import org.example.connectcg_be.repository.UserAvatarRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.service.impl.ChatRoomServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectChatCanonicalKeyTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserAvatarRepository userAvatarRepository;

    @InjectMocks
    private ChatRoomServiceImpl chatRoomService;

    @Test
    @DisplayName("Existing room is looked up by canonical pair key directly")
    void existingRoomIsLookedUpByCanonicalPairKey() {
        User u1 = createUser(5);
        User u2 = createUser(10);

        ChatRoom existingRoom = new ChatRoom();
        existingRoom.setId(77L);
        existingRoom.setType("DIRECT");
        existingRoom.setCanonicalPairKey("direct:5:10");
        existingRoom.setFirebaseRoomKey("firebase-key-77");

        when(chatRoomRepository.findByCanonicalPairKey("direct:5:10")).thenReturn(Optional.of(existingRoom));
        when(chatRoomMemberRepository.findByChatRoom_Id(77L)).thenReturn(Collections.emptyList());

        ChatRoomDTO dto = chatRoomService.getOrCreateDirectChat(u1, u2);

        assertNotNull(dto);
        assertEquals(77L, dto.getId());
        verify(chatRoomRepository).findByCanonicalPairKey("direct:5:10");
        // Must NOT attempt to create new room
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Symmetric user arguments produce the exact same canonical pair key")
    void symmetricUserOrderProducesSameCanonicalPairKey() {
        User u1 = createUser(10);
        User u2 = createUser(5);

        ChatRoom existingRoom = new ChatRoom();
        existingRoom.setId(77L);
        existingRoom.setType("DIRECT");
        existingRoom.setCanonicalPairKey("direct:5:10");
        existingRoom.setFirebaseRoomKey("firebase-key-77");

        // u1=10, u2=5 -> min is 5, max is 10 -> key is still direct:5:10
        when(chatRoomRepository.findByCanonicalPairKey("direct:5:10")).thenReturn(Optional.of(existingRoom));
        when(chatRoomMemberRepository.findByChatRoom_Id(77L)).thenReturn(Collections.emptyList());

        ChatRoomDTO dto = chatRoomService.getOrCreateDirectChat(u1, u2);

        assertNotNull(dto);
        assertEquals(77L, dto.getId());
        verify(chatRoomRepository).findByCanonicalPairKey("direct:5:10");
    }

    @Test
    @DisplayName("Creating new direct room sets the canonicalPairKey on the room")
    void creatingNewDirectRoomSetsCanonicalPairKey() {
        User u1 = createUser(3);
        User u2 = createUser(8);

        when(chatRoomRepository.findByCanonicalPairKey("direct:3:8")).thenReturn(Optional.empty());
        when(chatRoomMemberRepository.findByUser_Id(3)).thenReturn(Collections.emptyList());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            room.setId(99L);
            return room;
        });
        when(chatRoomMemberRepository.findByChatRoom_Id(99L)).thenReturn(Collections.emptyList());

        ChatRoomDTO dto = chatRoomService.getOrCreateDirectChat(u1, u2);

        assertNotNull(dto);
        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(captor.capture());
        assertEquals("direct:3:8", captor.getValue().getCanonicalPairKey());
        assertEquals("DIRECT", captor.getValue().getType());
    }

    private User createUser(Integer id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        return user;
    }
}
