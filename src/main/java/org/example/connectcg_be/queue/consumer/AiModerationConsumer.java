package org.example.connectcg_be.queue.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.AiModerationMessage;
import org.example.connectcg_be.service.PostService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiModerationConsumer {

    private final PostService postService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_AI)
    public void processAiModerationMessage(AiModerationMessage message) {
        log.info("Received AI moderation task [{}] for post ID: {}", message.getMessageId(), message.getPostId());
        try {
            postService.processAsyncModeration(
                    message.getPostId(),
                    message.getContent(),
                    message.getActionType(),
                    message.getUpdatedAt()
            );
            log.info("Successfully completed async AI moderation task [{}] for post ID: {}",
                    message.getMessageId(), message.getPostId());
        } catch (Exception e) {
            log.error("Error processing AI moderation task [{}] for post ID {}: {}",
                    message.getMessageId(), message.getPostId(), e.getMessage(), e);
            throw e; // Rethrow to let RabbitMQ retry with exponential backoff / dead-letter
        }
    }
}
