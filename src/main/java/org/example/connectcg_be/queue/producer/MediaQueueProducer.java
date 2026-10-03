package org.example.connectcg_be.queue.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.MediaProcessingMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaQueueProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.queue.enabled:true}")
    private boolean queueEnabled;

    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    /**
     * Đẩy tác vụ xử lý media (tạo thumbnail, nén background) vào hàng đợi RabbitMQ.
     * Trả về true nếu đẩy thành công, false nếu queue tắt hoặc lỗi broker.
     */
    public boolean enqueueMediaProcessing(Integer mediaId, String objectKey, String mediaType, String category, String contentType) {
        if (!queueEnabled) {
            log.info("Queue is disabled. Skipping async media processing for media [{}]", mediaId);
            return false;
        }

        MediaProcessingMessage message = MediaProcessingMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .mediaId(mediaId)
                .objectKey(objectKey)
                .mediaType(mediaType)
                .category(category)
                .contentType(contentType)
                .createdAt(Instant.now())
                .build();

        try {
            log.info("Publishing media processing task [{}] for media [{}] to queue", message.getMessageId(), mediaId);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_MEDIA,
                    message
            );
            log.debug("Successfully published media processing task [{}] to RabbitMQ", message.getMessageId());
            return true;
        } catch (Exception e) {
            log.error("Failed to publish media processing task to RabbitMQ for media [{}]: {}",
                    mediaId, e.getMessage(), e);
            return false;
        }
    }
}
