package org.example.connectcg_be.security;

import org.example.connectcg_be.entity.GroupMember;
import org.example.connectcg_be.entity.GroupMemberId;
import org.example.connectcg_be.repository.GroupMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("groupSecurity")
public class GroupSecurity {

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    /**
     * Kiểm tra xem người dùng hiện tại có phải là Admin của nhóm này không.
     * 
     * @param groupId ID của nhóm cần kiểm tra
     * @return true nếu là Admin của nhóm, ngược lại false
     */
    public boolean isGroupAdmin(Integer groupId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            return false;
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Integer userId = userPrincipal.getId();

        GroupMemberId memberId = new GroupMemberId();
        memberId.setGroupId(groupId);
        memberId.setUserId(userId);

        Optional<GroupMember> member = groupMemberRepository.findById(memberId);
        return member.isPresent() && "ADMIN".equals(member.get().getRole());
    }
}
