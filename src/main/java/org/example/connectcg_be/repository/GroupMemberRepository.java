package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.GroupMember;
import org.example.connectcg_be.entity.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    List<GroupMember> findAllByIdUserId(Integer userId);

    List<GroupMember> findAllByIdUserIdAndStatus(Integer userId, String status);

    List<GroupMember> findAllByIdGroupId(Integer groupId);

    List<GroupMember> findAllByIdGroupIdAndStatus(Integer groupId, String status);

    long countByIdGroupIdAndStatus(Integer groupId, String status);

    java.util.List<GroupMember> findAllByIdGroupIdAndRoleAndStatus(Integer groupId, String role, String status);

    @org.springframework.data.jpa.repository.Query("SELECT gm FROM GroupMember gm WHERE gm.id.groupId IN :groupIds AND gm.id.userId = :userId")
    List<GroupMember> findAllByIdGroupIdInAndIdUserId(
            @org.springframework.data.repository.query.Param("groupIds") java.util.Collection<Integer> groupIds,
            @org.springframework.data.repository.query.Param("userId") Integer userId);

    @org.springframework.data.jpa.repository.Query("SELECT gm.id.groupId, gm.status, count(gm) FROM GroupMember gm WHERE gm.id.groupId IN :groupIds AND gm.status IN ('REQUESTED', 'ACCEPTED') GROUP BY gm.id.groupId, gm.status")
    List<Object[]> countGroupMembersByStatus(
            @org.springframework.data.repository.query.Param("groupIds") java.util.Collection<Integer> groupIds);

}
