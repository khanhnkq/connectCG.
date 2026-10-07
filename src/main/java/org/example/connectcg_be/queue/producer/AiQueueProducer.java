package org.example.connectcg_be.queue.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.AiModerationMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiQueueProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.queue.enabled:true}")
    private boolean queueEnabled;

    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    /**
     * Đẩy tác vụ kiểm duyệt AI vào hàng đợi RabbitMQ.
     * Trả về true nếu đẩy thành công, false nếu queue tắt hoặc lỗi broker (để fallback sync).
     */
    public boolean enqueueModeration(Integer postId, String content, java.util.List<String> mediaUrls, String actionType, Integer authorId, Instant updatedAt) {
        if (!queueEnabled) {
            log.info("Queue is disabled. Skipping async enqueue for post [{}]", postId);
            return false;
        }

        AiModerationMessage message = AiModerationMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .postId(postId)
                .content(content)
                .mediaUrls(mediaUrls)
                .actionType(actionType)
                .authorId(authorId)
                .updatedAt(updatedAt)
                .createdAt(Instant.now())
                .build();

        try {
            log.info("Publishing AI moderation task [{}] for post [{}] with {} media items to queue",
                    message.getMessageId(), postId, mediaUrls != null ? mediaUrls.size() : 0);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_AI,
                    message
            );
            log.debug("Successfully published AI moderation task [{}] to RabbitMQ", message.getMessageId());
            return true;
        } catch (Exception e) {
            log.error("Failed to publish AI moderation task to RabbitMQ for post [{}]: {}",
                    postId, e.getMessage(), e);
            return false;
        }
    }

    public boolean enqueueModeration(Integer postId, String content, String actionType, Integer authorId, Instant updatedAt) {
        return enqueueModeration(postId, content, java.util.Collections.emptyList(), actionType, authorId, updatedAt);
    }

}
