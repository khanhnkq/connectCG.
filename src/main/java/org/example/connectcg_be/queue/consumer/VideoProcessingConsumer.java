package org.example.connectcg_be.queue.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.VideoProcessingMessage;
import org.example.connectcg_be.service.MediaUploadService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoProcessingConsumer {

    private final MediaUploadService mediaUploadService;

    /**
     * Consumer xử lý video ngầm (kiểm tra bypass <5MB hoặc <=720p, nén 720p và sinh thumbnail poster).
     * concurrency = "1" để bảo vệ an toàn 100% CPU/RAM của VPS 2 cores.
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_VIDEO, concurrency = "1")
    public void processVideoMessage(VideoProcessingMessage message) {
        log.info("Received video processing task [{}] for media ID: {}", message.getMessageId(), message.getMediaId());
        try {
            mediaUploadService.processAsyncVideo(
                    message.getMediaId(),
                    message.getObjectKey(),
                    message.getCategory(),
                    message.getContentType(),
                    message.getSizeBytes() != null ? message.getSizeBytes() : 0L
            );
            log.info("Successfully completed async video processing task [{}] for media ID: {}",
                    message.getMessageId(), message.getMediaId());
        } catch (Exception e) {
            log.error("Error processing video task [{}] for media ID {}: {}",
                    message.getMessageId(), message.getMediaId(), e.getMessage(), e);
            throw e; // Rethrow để RabbitMQ kích hoạt retry và chuyển DLQ nếu thất bại
        }
    }
}
