package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.TungNotificationDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface NotificationService {
    List<TungNotificationDTO> getMyNotifications(Integer userId);

    List<TungNotificationDTO> getMyNotifications(Integer userId, int page, int size);

    void markAsRead(Integer notificationId, Integer userId);

    void markAllAsRead(Integer userId);

    void deleteNotification(Integer notificationId, Integer userId);

    @Transactional
    void sendNotification(TungNotificationDTO dto, org.example.connectcg_be.entity.User receiver);

    @Transactional
    void sendNotification(TungNotificationDTO dto, org.example.connectcg_be.entity.User receiver,
            org.example.connectcg_be.entity.User actor);

    @Transactional
    void sendNotification(org.example.connectcg_be.entity.Notification notification);

    @Transactional
    void sendNotificationBatch(TungNotificationDTO dto, List<org.example.connectcg_be.entity.User> receivers,
                               org.example.connectcg_be.entity.User actor);

    void processFanoutNotification(org.example.connectcg_be.queue.dto.NotificationFanoutMessage message);
}
