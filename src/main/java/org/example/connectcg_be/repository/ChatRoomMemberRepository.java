package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.ChatRoomMember;
import org.example.connectcg_be.entity.ChatRoomMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMemberId> {
    @org.springframework.data.jpa.repository.Query("SELECT crm FROM ChatRoomMember crm " +
            "JOIN FETCH crm.chatRoom cr " +
            "JOIN FETCH crm.user " +
            "WHERE crm.user.id = :userId " +
            "ORDER BY COALESCE(cr.lastMessageAt, cr.createdAt) DESC")
    List<ChatRoomMember> findByUser_IdOrderByLastMessageAtDesc(
            @org.springframework.data.repository.query.Param("userId") Integer userId);

    List<ChatRoomMember> findByUser_Id(Integer userId);

    Optional<ChatRoomMember> findByChatRoom_IdAndUser_Id(Long chatRoomId, Integer userId);

    boolean existsByChatRoom_IdAndUser_Id(Long chatRoomId, Integer userId);

    List<ChatRoomMember> findByChatRoom_Id(Long chatRoomId);

    @EntityGraph(attributePaths = {"chatRoom", "user"})
    List<ChatRoomMember> findByChatRoom_IdIn(Collection<Long> chatRoomIds);

    void deleteByChatRoom_Id(Long chatRoomId);
}
