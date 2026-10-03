package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.TungNotificationDTO;
import org.example.connectcg_be.entity.Group;
import org.example.connectcg_be.entity.GroupMember;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.GroupMemberRepository;
import org.example.connectcg_be.repository.GroupRepository;
import org.example.connectcg_be.service.impl.GroupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceDeleteTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private GroupServiceImpl groupService;

    private User owner;
    private User memberUser;
    private Group group;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1);
        owner.setUsername("owner_user");
        owner.setRole("USER");

        memberUser = new User();
        memberUser.setId(2);
        memberUser.setUsername("member_user");
        memberUser.setRole("USER");

        group = new Group();
        group.setId(10);
        group.setName("Golang Developers");
        group.setOwner(owner);
        group.setIsDeleted(false);
    }

    @Test
    void deleteGroup_asOwner_dispatchesBatchNotifications() {
        when(groupRepository.findByIdAndIsDeletedFalse(10)).thenReturn(Optional.of(group));
        when(userService.findByIdUser(1)).thenReturn(owner);

        GroupMember gmOwner = new GroupMember();
        gmOwner.setUser(owner);
        GroupMember gmMember = new GroupMember();
        gmMember.setUser(memberUser);

        when(groupMemberRepository.findAllByIdGroupIdAndStatus(10, "ACCEPTED"))
                .thenReturn(List.of(gmOwner, gmMember));

        groupService.deleteGroup(10, 1);

        assertTrue(group.getIsDeleted());
        verify(groupRepository).save(group);

        ArgumentCaptor<TungNotificationDTO> dtoCaptor = ArgumentCaptor.forClass(TungNotificationDTO.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<User>> recipientsCaptor = ArgumentCaptor.forClass(List.class);

        verify(notificationService, times(2)).sendNotificationBatch(
                dtoCaptor.capture(),
                recipientsCaptor.capture(),
                eq(owner)
        );

        List<TungNotificationDTO> capturedDtos = dtoCaptor.getAllValues();
        List<List<User>> capturedRecipients = recipientsCaptor.getAllValues();

        // Check owner notification
        assertEquals("GROUP_DELETED", capturedDtos.get(0).getType());
        assertTrue(capturedDtos.get(0).getContent().contains("của bạn đã bị xóa"));
        assertEquals(1, capturedRecipients.get(0).size());
        assertEquals(1, capturedRecipients.get(0).get(0).getId());

        // Check member notification
        assertEquals("GROUP_DELETED", capturedDtos.get(1).getType());
        assertTrue(capturedDtos.get(1).getContent().contains("đã bị xóa bởi quản trị viên"));
        assertEquals(1, capturedRecipients.get(1).size());
        assertEquals(2, capturedRecipients.get(1).get(0).getId());

        // Crucial: ensure no synchronous individual notification was sent
        verify(notificationService, never()).sendNotification(any(), any(), any());
        verify(notificationService, never()).sendNotification(any(), any());
    }

    @Test
    void deleteGroup_asNonOwnerNonAdmin_throwsException() {
        User outsider = new User();
        outsider.setId(99);
        outsider.setRole("USER");

        when(groupRepository.findByIdAndIsDeletedFalse(10)).thenReturn(Optional.of(group));
        when(userService.findByIdUser(99)).thenReturn(outsider);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                groupService.deleteGroup(10, 99)
        );

        assertTrue(ex.getMessage().contains("Chỉ chủ nhóm hoặc admin"));
        assertFalse(group.getIsDeleted());
        verify(groupRepository, never()).save(group);
        verifyNoInteractions(groupMemberRepository);
        verifyNoInteractions(notificationService);
    }
}
