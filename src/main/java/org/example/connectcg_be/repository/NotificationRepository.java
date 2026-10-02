package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(Integer userId);
    Optional<Notification> findByIdAndUserId(Integer id, Integer userId);
}
