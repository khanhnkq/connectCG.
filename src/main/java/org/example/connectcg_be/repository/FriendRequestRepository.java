package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.FriendRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Integer> {
    // Tìm các lời mời đang chờ xử lý của người nhận, hỗ trợ phân trang cho infinite scroll
    Page<FriendRequest> findByReceiverIdAndStatusOrderByCreatedAtDesc(Integer receiverId, String status, Pageable pageable);

    // Tìm lời mời theo ID và người nhận (để bảo mật khi accept/reject)
    Optional<FriendRequest> findByIdAndReceiverId(Integer id, Integer receiverId);

    // Kiểm tra xem đã có lời mời PENDING giữa 2 người chưa
    boolean existsBySenderIdAndReceiverIdAndStatus(Integer senderId, Integer receiverId, String status);

    // Tìm lời mời để hủy
    Optional<FriendRequest> findBySenderIdAndReceiverIdAndStatus(Integer senderId, Integer receiverId, String status);

    @org.springframework.data.jpa.repository.Query("SELECT fr.receiver.id FROM FriendRequest fr WHERE fr.sender.id = :senderId AND fr.receiver.id IN :receiverIds AND fr.status = 'PENDING'")
    java.util.Set<Integer> findPendingReceiverIds(
            @org.springframework.data.repository.query.Param("senderId") Integer senderId,
            @org.springframework.data.repository.query.Param("receiverIds") java.util.Collection<Integer> receiverIds);

    @org.springframework.data.jpa.repository.Query("SELECT fr.sender.id FROM FriendRequest fr WHERE fr.receiver.id = :receiverId AND fr.sender.id IN :senderIds AND fr.status = 'PENDING'")
    java.util.Set<Integer> findPendingSenderIds(
            @org.springframework.data.repository.query.Param("receiverId") Integer receiverId,
            @org.springframework.data.repository.query.Param("senderIds") java.util.Collection<Integer> senderIds);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE FriendRequest fr SET fr.status = :newStatus, fr.respondedAt = :now WHERE fr.id = :id AND fr.receiver.id = :receiverId AND fr.status = 'PENDING'")
    int updateStatusIfPending(
            @org.springframework.data.repository.query.Param("id") Integer id,
            @org.springframework.data.repository.query.Param("receiverId") Integer receiverId,
            @org.springframework.data.repository.query.Param("newStatus") String newStatus,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}
