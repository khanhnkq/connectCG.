package org.example.connectcg_be.service;

import org.example.connectcg_be.entity.ChatRoom;
import org.example.connectcg_be.entity.Group;
import org.example.connectcg_be.entity.GroupMember;
import org.example.connectcg_be.entity.GroupMemberId;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.ChatRoomMemberRepository;
import org.example.connectcg_be.repository.ChatRoomRepository;
import org.example.connectcg_be.repository.GroupMemberRepository;
import org.example.connectcg_be.repository.GroupRepository;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthorizationServiceTest {
    private final ChatRoomRepository chatRoomRepository = mock(ChatRoomRepository.class);
    private final ChatRoomMemberRepository memberRepository = mock(ChatRoomMemberRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostAccessPolicy postAccessPolicy = mock(PostAccessPolicy.class);
    private final GroupRepository groupRepository = mock(GroupRepository.class);
    private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final WebSocketAuthorizationService service =
            new WebSocketAuthorizationService(
                    chatRoomRepository,
                    memberRepository,
                    postRepository,
                    postAccessPolicy,
                    groupRepository,
                    groupMemberRepository,
                    userRepository);

    @Test
    void chatTopicRequiresRoomMembership() {
        ChatRoom room = new ChatRoom();
        room.setId(11L);
        when(chatRoomRepository.findByFirebaseRoomKey("room-key")).thenReturn(Optional.of(room));
        when(memberRepository.existsByChatRoom_IdAndUser_Id(11L, 7)).thenReturn(true);

        assertTrue(service.canSubscribe(7, "/topic/chat/room-key/typing"));
        assertFalse(service.canSubscribe(8, "/topic/chat/room-key/seen"));
        assertFalse(service.canSubscribe(7, "/topic/chat/unknown/typing"));
    }

    @Test
    void onlyKnownSharedAndUserDestinationsAreAllowed() {
        assertTrue(service.canSubscribe(7, "/user/queue/notifications"));
        assertTrue(service.canSubscribe(7, "/topic/posts"));
        assertFalse(service.canSubscribe(7, "/topic/comments"));
        assertFalse(service.canSubscribe(7, "/topic/reactions"));
        assertFalse(service.canSubscribe(7, "/topic/private-data"));
        assertTrue(service.canSend("/app/chat/typing"));
        assertFalse(service.canSend("/app/admin/delete"));
    }

    @Test
    void postSpecificTopicUsesPostAccessPolicy() {
        Post post = new Post();
        post.setId(15);
        when(postRepository.findById(15)).thenReturn(Optional.of(post));
        when(postAccessPolicy.canView(post, 7)).thenReturn(true);

        assertTrue(service.canSubscribe(7, "/topic/posts/15/comments"));
        assertFalse(service.canSubscribe(8, "/topic/posts/15/comments"));
    }

    @Test
    void bannedGroupAdminCannotSubscribeToPendingModerationTopic() {
        User owner = new User();
        owner.setId(1);
        Group group = new Group();
        group.setId(5);
        group.setOwner(owner);

        User bannedAdmin = new User();
        bannedAdmin.setId(7);
        bannedAdmin.setRole("USER");
        GroupMember member = new GroupMember();
        member.setId(new GroupMemberId(5, 7));
        member.setRole("ADMIN");
        member.setStatus("BANNED");

        when(groupRepository.findByIdAndIsDeletedFalse(5)).thenReturn(Optional.of(group));
        when(userRepository.findById(7)).thenReturn(Optional.of(bannedAdmin));
        when(groupMemberRepository.findById(new GroupMemberId(5, 7))).thenReturn(Optional.of(member));

        assertFalse(service.canSubscribe(7, "/topic/groups/5/posts/pending"));
    }
}
