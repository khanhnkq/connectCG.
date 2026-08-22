package org.example.connectcg_be.security;

import org.example.connectcg_be.entity.Group;
import org.example.connectcg_be.entity.GroupMember;
import org.example.connectcg_be.entity.GroupMemberId;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.GroupMemberRepository;
import org.example.connectcg_be.repository.GroupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupSecurityTest {
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private GroupSecurity groupSecurity;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bannedAdminDoesNotRetainGroupAdminPermission() {
        UserPrincipal principal = new UserPrincipal(
                7, "admin", "admin@example.com", "password", true, false, false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        Group group = new Group();
        group.setId(10);
        User owner = new User();
        owner.setId(1);
        group.setOwner(owner);

        GroupMember member = new GroupMember();
        member.setId(new GroupMemberId(10, 7));
        member.setRole("ADMIN");
        member.setStatus("BANNED");

        when(groupRepository.findByIdAndIsDeletedFalse(10)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findById(new GroupMemberId(10, 7))).thenReturn(Optional.of(member));

        assertFalse(groupSecurity.isGroupAdmin(10));
    }
}
