package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(Integer userId);

    @EntityGraph(attributePaths = {"actor", "user"})
    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Integer id, Integer userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND (n.isRead = false OR n.isRead IS NULL)")
    int markAllAsReadByUserId(@Param("userId") Integer userId);

    Optional<Notification> findFirstByUserIdAndActorIdAndTypeAndTargetTypeAndTargetIdOrderByCreatedAtDesc(
            Integer userId, Integer actorId, String type, String targetType, Integer targetId);
}
