package org.example.connectcg_be.queue.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.MediaProcessingMessage;
import org.example.connectcg_be.service.MediaUploadService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaProcessingConsumer {

    private final MediaUploadService mediaUploadService;

    /**
     * Consumer xử lý ảnh và sinh thumbnail ngầm.
     * concurrency = "1-2" nhằm giới hạn tối đa 2 tác vụ nén đồng thời, bảo vệ bộ nhớ RAM VPS.
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_MEDIA, concurrency = "1-2")
    public void processMediaMessage(MediaProcessingMessage message) {
        log.info("Received media processing task [{}] for media ID: {}", message.getMessageId(), message.getMediaId());
        try {
            mediaUploadService.processAsyncMedia(
                    message.getMediaId(),
                    message.getObjectKey(),
                    message.getMediaType(),
                    message.getCategory()
            );
            log.info("Successfully completed async media processing task [{}] for media ID: {}",
                    message.getMessageId(), message.getMediaId());
        } catch (Exception e) {
            log.error("Error processing media task [{}] for media ID {}: {}",
                    message.getMessageId(), message.getMediaId(), e.getMessage(), e);
            throw e; // Rethrow để RabbitMQ kích hoạt retry và đưa vào DLQ nếu thất bại nhiều lần
        }
    }
}
