package org.example.connectcg_be.queue.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.VideoProcessingMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoQueueProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.queue.enabled:true}")
    private boolean queueEnabled;

    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    /**
     * Đẩy tác vụ xử lý video (nén 720p nếu cần, tạo poster thumbnail) vào hàng đợi RabbitMQ.
     * Trả về true nếu đẩy thành công, false nếu queue tắt hoặc lỗi broker.
     */
    public boolean enqueueVideoProcessing(Integer mediaId, String objectKey, String category, String contentType, Long sizeBytes) {
        if (!queueEnabled) {
            log.info("Queue is disabled. Skipping async video processing for media [{}]", mediaId);
            return false;
        }

        VideoProcessingMessage message = VideoProcessingMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .mediaId(mediaId)
                .objectKey(objectKey)
                .category(category)
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .createdAt(Instant.now())
                .build();

        try {
            log.info("Publishing video processing task [{}] for media [{}] to queue", message.getMessageId(), mediaId);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_VIDEO,
                    message
            );
            log.debug("Successfully published video processing task [{}] to RabbitMQ", message.getMessageId());
            return true;
        } catch (Exception e) {
            log.error("Failed to publish video processing task to RabbitMQ for media [{}]: {}",
                    mediaId, e.getMessage(), e);
            return false;
        }
    }
}
