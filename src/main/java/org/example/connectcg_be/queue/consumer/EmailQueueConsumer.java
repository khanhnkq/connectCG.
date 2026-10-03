package org.example.connectcg_be.queue.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.SendEmailMessage;
import org.example.connectcg_be.service.EmailService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailQueueConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_EMAIL)
    public void processEmailMessage(SendEmailMessage message) {
        log.info("Received email message [{}] from queue for: {}", message.getMessageId(), message.getTo());
        try {
            emailService.sendHtmlMessage(message.getTo(), message.getSubject(), message.getHtmlBody());
            log.info("Successfully processed email message [{}] for: {}", message.getMessageId(), message.getTo());
        } catch (Exception e) {
            log.error("Error processing email message [{}] for {}: {}",
                    message.getMessageId(), message.getTo(), e.getMessage(), e);
            // Ném ngoại lệ để Spring AMQP kích hoạt retry / chuyển DLQ nếu vượt ngưỡng retry
            throw e;
        }
    }
}
