package org.example.connectcg_be.queue;

import org.example.connectcg_be.queue.consumer.AiModerationConsumer;
import org.example.connectcg_be.queue.dto.AiModerationMessage;
import org.example.connectcg_be.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiModerationConsumerTest {

    @Mock
    private PostService postService;

    private AiModerationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AiModerationConsumer(postService);
    }

    @Test
    @DisplayName("Should delegate AI moderation task to PostService")
    void shouldDelegateToPostService() {
        Instant now = Instant.now();
        AiModerationMessage message = AiModerationMessage.builder()
                .postId(200)
                .content("Clean text")
                .actionType("CREATE")
                .authorId(10)
                .updatedAt(now)
                .build();

        consumer.processAiModerationMessage(message);

        verify(postService, times(1)).processAsyncModeration(200, "Clean text", "CREATE", now);
    }

    @Test
    @DisplayName("Should rethrow exception when PostService processing fails to trigger retry/DLQ")
    void shouldRethrowExceptionWhenPostServiceFails() {
        Instant now = Instant.now();
        AiModerationMessage message = AiModerationMessage.builder()
                .postId(201)
                .content("Error trigger text")
                .actionType("UPDATE")
                .authorId(10)
                .updatedAt(now)
                .build();

        doThrow(new RuntimeException("Gemini upstream 503"))
                .when(postService)
                .processAsyncModeration(201, "Error trigger text", "UPDATE", now);

        assertThrows(RuntimeException.class, () -> consumer.processAiModerationMessage(message));
        verify(postService, times(1)).processAsyncModeration(anyInt(), anyString(), anyString(), any(Instant.class));
    }
}
