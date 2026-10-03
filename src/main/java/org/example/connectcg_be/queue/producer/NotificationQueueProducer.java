package org.example.connectcg_be.queue.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.NotificationFanoutMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueueProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.queue.enabled:true}")
    private boolean queueEnabled;

    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    /**
     * Đẩy tác vụ fan-out thông báo tới nhiều người nhận vào hàng đợi RabbitMQ.
     * Trả về true nếu đẩy thành công, false nếu queue tắt hoặc lỗi broker.
     */
    public boolean enqueueFanout(List<Integer> recipientUserIds, Integer actorId, String content,
                                 String type, String targetType, Integer targetId) {
        if (!queueEnabled) {
            log.info("Queue is disabled. Skipping async notification fan-out for {} recipients",
                    recipientUserIds != null ? recipientUserIds.size() : 0);
            return false;
        }

        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            log.debug("Recipient list is empty. Skipping notification enqueue.");
            return true;
        }

        NotificationFanoutMessage message = NotificationFanoutMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .recipientUserIds(recipientUserIds)
                .actorId(actorId)
                .content(content)
                .type(type)
                .targetType(targetType)
                .targetId(targetId)
                .createdAt(Instant.now())
                .build();

        try {
            log.info("Publishing notification fan-out task [{}] for {} recipients to queue",
                    message.getMessageId(), recipientUserIds.size());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_NOTIFICATION,
                    message
            );
            log.debug("Successfully published notification fan-out task [{}] to RabbitMQ", message.getMessageId());
            return true;
        } catch (Exception e) {
            log.error("Failed to publish notification fan-out task to RabbitMQ: {}", e.getMessage(), e);
            return false;
        }
    }
}
