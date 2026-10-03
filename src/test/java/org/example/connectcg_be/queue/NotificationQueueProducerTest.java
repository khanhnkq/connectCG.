package org.example.connectcg_be.queue;

import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.NotificationFanoutMessage;
import org.example.connectcg_be.queue.producer.NotificationQueueProducer;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationQueueProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private NotificationQueueProducer producer;

    @BeforeEach
    void setUp() {
        producer = new NotificationQueueProducer(rabbitTemplate);
        ReflectionTestUtils.setField(producer, "queueEnabled", true);
    }

    @Test
    @DisplayName("Should publish notification fan-out task when queue is enabled")
    void shouldPublishWhenQueueEnabled() {
        List<Integer> recipientIds = List.of(1, 2, 3);
        Integer actorId = 10;
        String content = "User 10 requested to join group";
        String type = "GROUP_REQUEST";
        String targetType = "GROUP";
        Integer targetId = 99;

        boolean result = producer.enqueueFanout(recipientIds, actorId, content, type, targetType, targetId);

        assertTrue(result);

        ArgumentCaptor<NotificationFanoutMessage> captor = ArgumentCaptor.forClass(NotificationFanoutMessage.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_NOTIFICATION),
                captor.capture()
        );

        NotificationFanoutMessage captured = captor.getValue();
        assertEquals(recipientIds, captured.getRecipientUserIds());
        assertEquals(actorId, captured.getActorId());
        assertEquals(content, captured.getContent());
        assertEquals(type, captured.getType());
        assertEquals(targetType, captured.getTargetType());
        assertEquals(targetId, captured.getTargetId());
        assertNotNull(captured.getMessageId());
        assertNotNull(captured.getCreatedAt());
    }

    @Test
    @DisplayName("Should return true and skip when recipient list is empty")
    void shouldSkipWhenRecipientListEmpty() {
        boolean result = producer.enqueueFanout(List.of(), 10, "test", "TYPE", "TARGET", 1);

        assertTrue(result);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Should return false when queue is disabled")
    void shouldReturnFalseWhenQueueDisabled() {
        ReflectionTestUtils.setField(producer, "queueEnabled", false);

        boolean result = producer.enqueueFanout(List.of(1, 2), 10, "test", "TYPE", "TARGET", 1);

        assertFalse(result);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Should handle RabbitTemplate exception gracefully")
    void shouldHandleExceptionGracefully() {
        doThrow(new AmqpException("Broker error"))
                .when(rabbitTemplate)
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE_DIRECT), eq(RabbitMQConfig.ROUTING_KEY_NOTIFICATION), any(NotificationFanoutMessage.class));

        boolean result = producer.enqueueFanout(List.of(1, 2), 10, "test", "TYPE", "TARGET", 1);

        assertFalse(result);
    }
}
