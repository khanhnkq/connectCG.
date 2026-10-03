package org.example.connectcg_be.queue.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.NotificationFanoutMessage;
import org.example.connectcg_be.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationFanoutConsumer {

    private final NotificationService notificationService;

    /**
     * Consumer xử lý fan-out thông báo ngầm cho nhiều người nhận.
     * concurrency = "1-2" nhằm giới hạn luồng xử lý đồng thời, bảo vệ RAM và connection pool.
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION, concurrency = "1-2")
    public void processFanoutMessage(NotificationFanoutMessage message) {
        log.info("Received notification fan-out task [{}] for {} recipients",
                message.getMessageId(),
                message.getRecipientUserIds() != null ? message.getRecipientUserIds().size() : 0);
        try {
            notificationService.processFanoutNotification(message);
            log.info("Successfully completed notification fan-out task [{}]", message.getMessageId());
        } catch (Exception e) {
            log.error("Error processing notification fan-out task [{}]: {}",
                    message.getMessageId(), e.getMessage(), e);
            throw e; // Rethrow để RabbitMQ retry hoặc đưa vào DLQ
        }
    }
}
