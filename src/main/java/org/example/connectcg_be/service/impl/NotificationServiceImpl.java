package org.example.connectcg_be.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.dto.TungNotificationDTO;
import org.example.connectcg_be.entity.Notification;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.entity.UserAvatar;
import org.example.connectcg_be.entity.UserProfile;
import org.example.connectcg_be.repository.NotificationRepository;
import org.example.connectcg_be.repository.UserAvatarRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.queue.dto.NotificationFanoutMessage;
import org.example.connectcg_be.queue.producer.NotificationQueueProducer;
import org.example.connectcg_be.service.NotificationService;
import org.example.connectcg_be.realtime.RealtimeEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
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
        return getMyNotifications(userId, 0, 50);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TungNotificationDTO> getMyNotifications(Integer userId, int page, int size) {
        if (userId == null) {
            return List.of();
        }
        int safePage = Math.max(0, page);
        int safeSize = (size <= 0 || size > 100) ? 20 : size;
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Notification> notificationPage = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        return mapNotificationsToDTOs(notificationPage.getContent());
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
    public void markAllAsRead(Integer userId) {
        if (userId == null) {
            return;
        }
        int updated = notificationRepository.markAllAsReadByUserId(userId);
        log.info("mark_all_as_read_success userId={} updatedCount={}", userId, updated);
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

    private List<TungNotificationDTO> mapNotificationsToDTOs(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Integer> actorIds = notifications.stream()
                .map(Notification::getActor)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Integer, String> actorNameMap = new HashMap<>();
        Map<Integer, String> actorAvatarMap = new HashMap<>();

        if (!actorIds.isEmpty()) {
            List<UserProfile> profiles = userProfileRepository.findAllByUserIdIn(actorIds);
            for (UserProfile profile : profiles) {
                if (profile.getUser() != null && profile.getUser().getId() != null) {
                    String fullName = profile.getFullName();
                    if (fullName != null && !fullName.isBlank()) {
                        actorNameMap.put(profile.getUser().getId(), fullName);
                    }
                }
            }

            List<UserAvatar> avatars = userAvatarRepository.findCurrentByUserIds(actorIds);
            for (UserAvatar avatar : avatars) {
                if (avatar.getUser() != null && avatar.getUser().getId() != null && avatar.getMedia() != null) {
                    actorAvatarMap.put(avatar.getUser().getId(), avatar.getMedia().getUrl());
                }
            }
        }

        String defaultSystemAvatar = "https://cdn-icons-png.flaticon.com/512/149/149071.png";

        List<TungNotificationDTO> dtos = new ArrayList<>(notifications.size());
        for (Notification n : notifications) {
            TungNotificationDTO dto = new TungNotificationDTO();
            dto.setId(n.getId());
            dto.setContent(n.getContent());
            dto.setType(n.getType());
            dto.setTargetType(n.getTargetType());
            dto.setTargetId(n.getTargetId());
            dto.setIsRead(n.getIsRead() != null ? n.getIsRead() : false);
            dto.setCreatedAt(n.getCreatedAt());

            User actor = n.getActor();
            if (actor != null) {
                String name = actorNameMap.get(actor.getId());
                if (name == null || name.isBlank()) {
                    name = actor.getUsername();
                }
                dto.setActorName(name);
                dto.setActorAvatar(actorAvatarMap.getOrDefault(actor.getId(), defaultSystemAvatar));
            } else {
                dto.setActorName("Hệ thống");
                dto.setActorAvatar(defaultSystemAvatar);
            }
            dtos.add(dto);
        }

        return dtos;
    }
}
