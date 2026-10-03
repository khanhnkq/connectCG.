package org.example.connectcg_be.queue.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.SendEmailMessage;
import org.example.connectcg_be.service.EmailService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailQueueProducer {

    private final RabbitTemplate rabbitTemplate;
    private final EmailService emailService;

    @Value("${app.queue.enabled:true}")
    private boolean queueEnabled;

    /**
     * Gửi yêu cầu gửi email vào hàng đợi bất đồng bộ.
     * Nếu queue bị tắt hoặc broker lỗi tạm thời, fallback sang gửi trực tiếp.
     */
    public void sendEmail(String to, String subject, String htmlBody, String templateType) {
        SendEmailMessage message = SendEmailMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .to(to)
                .subject(subject)
                .htmlBody(htmlBody)
                .templateType(templateType)
                .createdAt(Instant.now())
                .build();

        if (!queueEnabled) {
            log.info("Queue is disabled. Falling back to synchronous email delivery for {}", to);
            emailService.sendHtmlMessage(to, subject, htmlBody);
            return;
        }

        try {
            log.info("Publishing email message [{}] to queue for recipient: {}", message.getMessageId(), to);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_EMAIL,
                    message
            );
            log.debug("Successfully published email message [{}] to RabbitMQ", message.getMessageId());
        } catch (Exception e) {
            log.error("Failed to publish email message to RabbitMQ, falling back to sync sending for {}: {}",
                    to, e.getMessage(), e);
            emailService.sendHtmlMessage(to, subject, htmlBody);
        }
    }

    public void sendEmail(String to, String subject, String htmlBody) {
        sendEmail(to, subject, htmlBody, "GENERAL");
    }
}
