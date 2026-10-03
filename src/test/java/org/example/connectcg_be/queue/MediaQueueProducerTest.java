package org.example.connectcg_be.queue;

import org.example.connectcg_be.config.RabbitMQConfig;
import org.example.connectcg_be.queue.dto.MediaProcessingMessage;
import org.example.connectcg_be.queue.producer.MediaQueueProducer;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaQueueProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private MediaQueueProducer mediaQueueProducer;

    @BeforeEach
    void setUp() {
        mediaQueueProducer = new MediaQueueProducer(rabbitTemplate);
        ReflectionTestUtils.setField(mediaQueueProducer, "queueEnabled", true);
    }

    @Test
    @DisplayName("Should publish media processing task to RabbitMQ when queue is enabled")
    void shouldPublishMediaTaskWhenQueueEnabled() {
        Integer mediaId = 42;
        String objectKey = "post/2026/08/sample.jpg";
        String mediaType = "IMAGE";
        String category = "POST";
        String contentType = "image/jpeg";

        boolean result = mediaQueueProducer.enqueueMediaProcessing(mediaId, objectKey, mediaType, category, contentType);

        assertTrue(result);

        ArgumentCaptor<MediaProcessingMessage> captor = ArgumentCaptor.forClass(MediaProcessingMessage.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_MEDIA),
                captor.capture()
        );

        MediaProcessingMessage captured = captor.getValue();
        assertEquals(mediaId, captured.getMediaId());
        assertEquals(objectKey, captured.getObjectKey());
        assertEquals(mediaType, captured.getMediaType());
        assertEquals(category, captured.getCategory());
        assertEquals(contentType, captured.getContentType());
        assertNotNull(captured.getMessageId());
        assertNotNull(captured.getCreatedAt());
    }

    @Test
    @DisplayName("Should skip publishing when queue is disabled")
    void shouldSkipWhenQueueDisabled() {
        ReflectionTestUtils.setField(mediaQueueProducer, "queueEnabled", false);

        boolean result = mediaQueueProducer.enqueueMediaProcessing(42, "key", "IMAGE", "POST", "image/jpeg");

        assertFalse(result);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Should return false and handle error gracefully when RabbitTemplate throws exception")
    void shouldHandleExceptionGracefully() {
        doThrow(new AmqpException("Broker connection refused"))
                .when(rabbitTemplate)
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE_DIRECT), eq(RabbitMQConfig.ROUTING_KEY_MEDIA), any(MediaProcessingMessage.class));

        boolean result = mediaQueueProducer.enqueueMediaProcessing(42, "key", "IMAGE", "POST", "image/jpeg");

        assertFalse(result);
    }
}
