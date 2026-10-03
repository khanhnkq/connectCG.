package org.example.connectcg_be.queue;

import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.AiModerationMessage;
import org.example.connectcg_be.queue.producer.AiQueueProducer;
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

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiQueueProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AiQueueProducer aiQueueProducer;

    @BeforeEach
    void setUp() {
        aiQueueProducer = new AiQueueProducer(rabbitTemplate);
        ReflectionTestUtils.setField(aiQueueProducer, "queueEnabled", true);
    }

    @Test
    @DisplayName("Should publish AI moderation task to RabbitMQ when queue is enabled")
    void shouldPublishAiTaskWhenQueueEnabled() {
        Integer postId = 100;
        String content = "Hello world, this is a clean post";
        String actionType = "CREATE";
        Integer authorId = 5;
        Instant updatedAt = Instant.now();

        boolean result = aiQueueProducer.enqueueModeration(postId, content, actionType, authorId, updatedAt);

        assertTrue(result);

        ArgumentCaptor<AiModerationMessage> captor = ArgumentCaptor.forClass(AiModerationMessage.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_AI),
                captor.capture()
        );

        AiModerationMessage captured = captor.getValue();
        assertEquals(postId, captured.getPostId());
        assertEquals(content, captured.getContent());
        assertEquals(actionType, captured.getActionType());
        assertEquals(authorId, captured.getAuthorId());
        assertEquals(updatedAt, captured.getUpdatedAt());
        assertNotNull(captured.getMessageId());
    }

    @Test
    @DisplayName("Should return false and skip publish when queue is disabled")
    void shouldSkipPublishWhenQueueDisabled() {
        ReflectionTestUtils.setField(aiQueueProducer, "queueEnabled", false);

        boolean result = aiQueueProducer.enqueueModeration(101, "Test content", "CREATE", 5, Instant.now());

        assertFalse(result);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Should return false when RabbitTemplate throws exception")
    void shouldReturnFalseWhenRabbitThrowsException() {
        doThrow(new AmqpException("Broker down"))
                .when(rabbitTemplate)
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE_DIRECT), eq(RabbitMQConfig.ROUTING_KEY_AI), any(AiModerationMessage.class));

        boolean result = aiQueueProducer.enqueueModeration(102, "Content", "UPDATE", 5, Instant.now());

        assertFalse(result);
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(AiModerationMessage.class));
    }
}
