package org.example.connectcg_be.queue;

import org.example.connectcg_be.queue.consumer.MediaProcessingConsumer;
import org.example.connectcg_be.queue.dto.MediaProcessingMessage;
import org.example.connectcg_be.service.MediaUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaProcessingConsumerTest {

    @Mock
    private MediaUploadService mediaUploadService;

    @InjectMocks
    private MediaProcessingConsumer mediaProcessingConsumer;

    @Test
    @DisplayName("Should invoke MediaUploadService.processAsyncMedia when message received")
    void shouldInvokeServiceOnValidMessage() {
        MediaProcessingMessage message = MediaProcessingMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .mediaId(101)
                .objectKey("post/2026/08/sample.png")
                .mediaType("IMAGE")
                .category("POST")
                .contentType("image/png")
                .createdAt(Instant.now())
                .build();

        mediaProcessingConsumer.processMediaMessage(message);

        verify(mediaUploadService, times(1)).processAsyncMedia(
                101,
                "post/2026/08/sample.png",
                "IMAGE",
                "POST"
        );
    }

    @Test
    @DisplayName("Should rethrow exception when MediaUploadService throws error for DLQ / retry")
    void shouldRethrowExceptionWhenServiceFails() {
        MediaProcessingMessage message = MediaProcessingMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .mediaId(102)
                .objectKey("post/2026/08/sample.png")
                .mediaType("IMAGE")
                .category("POST")
                .contentType("image/png")
                .createdAt(Instant.now())
                .build();

        doThrow(new RuntimeException("MinIO error"))
                .when(mediaUploadService)
                .processAsyncMedia(anyInt(), anyString(), anyString(), anyString());

        assertThrows(RuntimeException.class, () -> mediaProcessingConsumer.processMediaMessage(message));
    }
}
