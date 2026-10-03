package org.example.connectcg_be.queue;

import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.SendEmailMessage;
import org.example.connectcg_be.queue.producer.EmailQueueProducer;
import org.example.connectcg_be.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailQueueProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private EmailService emailService;

    private EmailQueueProducer emailQueueProducer;

    @BeforeEach
    void setUp() {
        emailQueueProducer = new EmailQueueProducer(rabbitTemplate, emailService);
        ReflectionTestUtils.setField(emailQueueProducer, "queueEnabled", true);
    }

    @Test
    @DisplayName("Should publish email message to RabbitMQ when queue is enabled")
    void shouldPublishEmailMessageToRabbitMQ() {
        String to = "user@example.com";
        String subject = "Verify your account";
        String body = "<p>Welcome to ConnectCG</p>";

        emailQueueProducer.sendEmail(to, subject, body, "VERIFY_EMAIL");

        ArgumentCaptor<SendEmailMessage> captor = ArgumentCaptor.forClass(SendEmailMessage.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_EMAIL),
                captor.capture()
        );

        SendEmailMessage captured = captor.getValue();
        assertEquals(to, captured.getTo());
        assertEquals(subject, captured.getSubject());
        assertEquals(body, captured.getHtmlBody());
        assertEquals("VERIFY_EMAIL", captured.getTemplateType());
        assertNotNull(captured.getMessageId());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Should fallback to synchronous emailService when queue is disabled")
    void shouldFallbackToSyncWhenQueueDisabled() {
        ReflectionTestUtils.setField(emailQueueProducer, "queueEnabled", false);

        String to = "user@example.com";
        String subject = "Password Reset";
        String body = "<p>Reset your password</p>";

        emailQueueProducer.sendEmail(to, subject, body, "FORGOT_PASSWORD");

        verifyNoInteractions(rabbitTemplate);
        verify(emailService, times(1)).sendHtmlMessage(to, subject, body);
    }

    @Test
    @DisplayName("Should fallback to synchronous emailService when RabbitMQ publish fails with exception")
    void shouldFallbackToSyncWhenRabbitPublishFails() {
        String to = "user@example.com";
        String subject = "Notification";
        String body = "<p>New update</p>";

        doThrow(new AmqpException("Broker unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE_DIRECT), eq(RabbitMQConfig.ROUTING_KEY_EMAIL), any(SendEmailMessage.class));

        emailQueueProducer.sendEmail(to, subject, body, "NOTIFICATION");

        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(SendEmailMessage.class));
        verify(emailService, times(1)).sendHtmlMessage(to, subject, body);
    }
}
