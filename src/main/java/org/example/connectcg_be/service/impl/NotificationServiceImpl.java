package org.example.connectcg_be.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.dto.TungNotificationDTO;
import org.example.connectcg_be.entity.Notification;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.entity.UserAvatar;
import org.example.connectcg_be.repository.NotificationRepository;
import org.example.connectcg_be.repository.UserAvatarRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.queue.dto.NotificationFanoutMessage;
import org.example.connectcg_be.queue.producer.NotificationQueueProducer;
import org.example.connectcg_be.service.NotificationService;
import org.example.connectcg_be.realtime.RealtimeEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserAvatarRepository userAvatarRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final NotificationQueueProducer notificationQueueProducer;

    @Override
    @Transactional(readOnly = true)
    public List<TungNotificationDTO> getMyNotifications(Integer userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markAsRead(Integer notificationId, Integer userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        if (notification.getUser() == null || !notification.getUser().getId().equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác trên thông báo này");
        }
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void deleteNotification(Integer notificationId, Integer userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        if (notification.getUser() == null || !notification.getUser().getId().equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác trên thông báo này");
        }
        notificationRepository.delete(notification);
    }

    @Transactional
    @Override
    public void sendNotification(TungNotificationDTO dto, User receiver) {
        sendNotification(dto, receiver, null);
    }

    @Transactional
    @Override
    public void sendNotification(TungNotificationDTO dto, User receiver,
            User actor) {
        Notification entity = new Notification();
        entity.setUser(receiver);
        entity.setActor(actor);
        entity.setContent(dto.getContent());
        entity.setType(dto.getType());
        entity.setTargetType(dto.getTargetType());
        entity.setTargetId(dto.getTargetId());
        entity.setIsRead(false);
        entity.setCreatedAt(java.time.Instant.now());
        Notification saved = notificationRepository.save(entity);

        // Fetch actor info for real-time WebSocket display
        String actorName = "Hệ thống";
        String actorAvatarUrl = "https://cdn-icons-png.flaticon.com/512/149/149071.png";

        if (actor != null) {
            actorName = userProfileRepository.findByUserId(actor.getId())
                    .map(org.example.connectcg_be.entity.UserProfile::getFullName)
                    .orElse(actor.getUsername());

            org.example.connectcg_be.entity.UserAvatar avatar = userAvatarRepository
                    .findByUserIdAndIsCurrentTrue(actor.getId());
            if (avatar != null && avatar.getMedia() != null) {
                actorAvatarUrl = avatar.getMedia().getUrl();
            }
        }

        // Create an isolated snapshot DTO for deferred realtime transmission
        TungNotificationDTO outgoingDto = new TungNotificationDTO();
        outgoingDto.setId(saved.getId());
        outgoingDto.setContent(dto.getContent());
        outgoingDto.setType(dto.getType());
        outgoingDto.setTargetType(dto.getTargetType());
        outgoingDto.setTargetId(dto.getTargetId());
        outgoingDto.setIsRead(false);
        outgoingDto.setCreatedAt(saved.getCreatedAt());
        outgoingDto.setActorName(actorName);
        outgoingDto.setActorAvatar(actorAvatarUrl);

        // Also update caller's dto in place
        dto.setId(saved.getId());
        dto.setCreatedAt(saved.getCreatedAt());
        dto.setIsRead(false);
        dto.setActorName(actorName);
        dto.setActorAvatar(actorAvatarUrl);

        realtimeEventPublisher.sendToUser(
                receiver.getUsername(),
                "/queue/notifications",
                outgoingDto);
    }

    @Override
    @Transactional
    public void sendNotification(Notification notification) {
        notification.setCreatedAt(Instant.now());
        Notification saved = notificationRepository.save(notification);

        // Convert to DTO with actor info for frontend
        TungNotificationDTO dto = mapToDTO(saved);

        realtimeEventPublisher.sendToUser(
                saved.getUser().getUsername(),
                "/queue/notifications",
                dto);
    }

    private TungNotificationDTO mapToDTO(Notification notification) {
        String actorName = notification.getActor() != null ? notification.getActor().getUsername() : "System";
        String actorAvatarUrl = "https://cdn-icons-png.flaticon.com/512/149/149071.png";

        if (notification.getActor() != null) {
            actorName = userProfileRepository.findByUserId(notification.getActor().getId())
                    .map(org.example.connectcg_be.entity.UserProfile::getFullName)
                    .orElse(notification.getActor().getUsername());

            UserAvatar avatar = userAvatarRepository.findByUserIdAndIsCurrentTrue(notification.getActor().getId());
            if (avatar != null && avatar.getMedia() != null) {
                actorAvatarUrl = avatar.getMedia().getUrl();
            }
        }

        return new TungNotificationDTO(
                notification.getId(),
                notification.getContent(),
                notification.getType(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getIsRead(),
                notification.getCreatedAt(),
                actorName,
                actorAvatarUrl);
    }

    @Override
    @Transactional
    public void sendNotificationBatch(TungNotificationDTO dto, List<User> receivers, User actor) {
        if (receivers == null || receivers.isEmpty()) {
            return;
        }

        List<Integer> recipientIds = receivers.stream()
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList();

        if (recipientIds.isEmpty()) {
            return;
        }

        Integer actorId = actor != null ? actor.getId() : null;

        // Đẩy vào RabbitMQ queue để fan-out bất đồng bộ
        boolean enqueued = notificationQueueProducer != null && notificationQueueProducer.enqueueFanout(
                recipientIds,
                actorId,
                dto.getContent(),
                dto.getType(),
                dto.getTargetType(),
                dto.getTargetId()
        );

        if (!enqueued) {
            // Fallback đồng bộ khi queue tắt hoặc broker không khả dụng
            log.info("Queue unavailable, falling back to sync batch notification for {} users", recipientIds.size());
            processFanoutInternal(recipientIds, actor, dto.getContent(), dto.getType(), dto.getTargetType(), dto.getTargetId());
        }
    }

    @Override
    @Transactional
    public void processFanoutNotification(NotificationFanoutMessage message) {
        if (message == null || message.getRecipientUserIds() == null || message.getRecipientUserIds().isEmpty()) {
            return;
        }

        User actor = null;
        if (message.getActorId() != null) {
            actor = userRepository.findById(message.getActorId()).orElse(null);
        }

        processFanoutInternal(
                message.getRecipientUserIds(),
                actor,
                message.getContent(),
                message.getType(),
                message.getTargetType(),
                message.getTargetId()
        );
    }

    private void processFanoutInternal(List<Integer> recipientUserIds, User actor,
                                       String content, String type, String targetType, Integer targetId) {
        String actorName = "Hệ thống";
        String actorAvatarUrl = "https://cdn-icons-png.flaticon.com/512/149/149071.png";

        if (actor != null) {
            actorName = userProfileRepository.findByUserId(actor.getId())
                    .map(org.example.connectcg_be.entity.UserProfile::getFullName)
                    .orElse(actor.getUsername());

            UserAvatar avatar = userAvatarRepository.findByUserIdAndIsCurrentTrue(actor.getId());
            if (avatar != null && avatar.getMedia() != null) {
                actorAvatarUrl = avatar.getMedia().getUrl();
            }
        }

        // Chia batch 50 người nhận/lần để bảo vệ HikariCP và bộ nhớ
        int batchSize = 50;
        for (int i = 0; i < recipientUserIds.size(); i += batchSize) {
            List<Integer> chunkIds = recipientUserIds.subList(i, Math.min(i + batchSize, recipientUserIds.size()));
            List<User> recipients = userRepository.findAllById(chunkIds);
            List<Notification> entities = new ArrayList<>();
            Instant now = Instant.now();

            for (User receiver : recipients) {
                Notification entity = new Notification();
                entity.setUser(receiver);
                entity.setActor(actor);
                entity.setContent(content);
                entity.setType(type);
                entity.setTargetType(targetType);
                entity.setTargetId(targetId);
                entity.setIsRead(false);
                entity.setCreatedAt(now);
                entities.add(entity);
            }

            List<Notification> savedEntities = notificationRepository.saveAll(entities);

            // Gửi realtime qua STOMP cho từng user
            for (Notification saved : savedEntities) {
                TungNotificationDTO outgoingDto = new TungNotificationDTO();
                outgoingDto.setId(saved.getId());
                outgoingDto.setContent(saved.getContent());
                outgoingDto.setType(saved.getType());
                outgoingDto.setTargetType(saved.getTargetType());
                outgoingDto.setTargetId(saved.getTargetId());
                outgoingDto.setIsRead(false);
                outgoingDto.setCreatedAt(saved.getCreatedAt());
                outgoingDto.setActorName(actorName);
                outgoingDto.setActorAvatar(actorAvatarUrl);

                realtimeEventPublisher.sendToUser(
                        saved.getUser().getUsername(),
                        "/queue/notifications",
                        outgoingDto);
            }
        }
        log.info("fanout_notifications_completed totalRecipients={}", recipientUserIds.size());
    }
}
