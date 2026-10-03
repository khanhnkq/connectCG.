package org.example.connectcg_be.queue;

import org.example.connectcg_be.queue.consumer.NotificationFanoutConsumer;
import org.example.connectcg_be.queue.dto.NotificationFanoutMessage;
import org.example.connectcg_be.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationFanoutConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationFanoutConsumer consumer;

    @Test
    @DisplayName("Should invoke NotificationService.processFanoutNotification on message")
    void shouldInvokeServiceOnMessage() {
        NotificationFanoutMessage message = NotificationFanoutMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .recipientUserIds(List.of(1, 2, 3))
                .actorId(10)
                .content("New update")
                .type("UPDATE")
                .targetType("GROUP")
                .targetId(50)
                .createdAt(Instant.now())
                .build();

        consumer.processFanoutMessage(message);

        verify(notificationService, times(1)).processFanoutNotification(message);
    }

    @Test
    @DisplayName("Should rethrow exception when service fails")
    void shouldRethrowExceptionWhenServiceFails() {
        NotificationFanoutMessage message = NotificationFanoutMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .recipientUserIds(List.of(1, 2))
                .actorId(10)
                .content("Fail")
                .build();

        doThrow(new RuntimeException("Database error"))
                .when(notificationService)
                .processFanoutNotification(any());

        assertThrows(RuntimeException.class, () -> consumer.processFanoutMessage(message));
    }
}
