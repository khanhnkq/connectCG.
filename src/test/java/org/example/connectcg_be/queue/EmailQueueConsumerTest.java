package org.example.connectcg_be.queue;

import org.example.connectcg_be.queue.consumer.EmailQueueConsumer;
import org.example.connectcg_be.queue.dto.SendEmailMessage;
import org.example.connectcg_be.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailQueueConsumerTest {

    @Mock
    private EmailService emailService;

    private EmailQueueConsumer emailQueueConsumer;

    @BeforeEach
    void setUp() {
        emailQueueConsumer = new EmailQueueConsumer(emailService);
    }

    @Test
    @DisplayName("Should successfully process email message and call emailService")
    void shouldProcessEmailMessage() {
        SendEmailMessage message = SendEmailMessage.builder()
                .to("recipient@example.com")
                .subject("Test Subject")
                .htmlBody("<p>Test Body</p>")
                .templateType("VERIFY_EMAIL")
                .build();

        emailQueueConsumer.processEmailMessage(message);

        verify(emailService, times(1)).sendHtmlMessage(
                "recipient@example.com",
                "Test Subject",
                "<p>Test Body</p>"
        );
    }

    @Test
    @DisplayName("Should rethrow exception when emailService fails to trigger retry/DLQ")
    void shouldRethrowExceptionWhenEmailServiceFails() {
        SendEmailMessage message = SendEmailMessage.builder()
                .to("recipient@example.com")
                .subject("Test Subject")
                .htmlBody("<p>Test Body</p>")
                .build();

        doThrow(new RuntimeException("SMTP connection timeout"))
                .when(emailService)
                .sendHtmlMessage(anyString(), anyString(), anyString());

        assertThrows(RuntimeException.class, () -> emailQueueConsumer.processEmailMessage(message));
        verify(emailService, times(1)).sendHtmlMessage(anyString(), anyString(), anyString());
    }
}
