package org.example.connectcg_be.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.connectcg_be.dto.AiModerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiModerationCacheTest {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private GeminiModerationCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new GeminiModerationCache(
                redisTemplate,
                new ObjectMapper(),
                "test",
                true,
                Duration.ofHours(24),
                Duration.ofDays(7));
    }

    @Test
    void readsCachedResultWithoutPuttingContentInKey() throws Exception {
        AiModerationResult expected = new AiModerationResult(0.1, "SAFE", "Hợp lệ");
        when(valueOperations.get(anyString())).thenReturn(new ObjectMapper().writeValueAsString(expected));

        AiModerationResult actual = cache.find("Nội dung riêng tư", "gemini-model", "v1").orElseThrow();

        assertEquals("SAFE", actual.getLabel());
        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations).get(keyCaptor.capture());
        assertTrue(keyCaptor.getValue().startsWith("connect:test:ai-moderation:v1:"));
        assertFalse(keyCaptor.getValue().contains("Nội dung riêng tư"));
    }

    @Test
    void storesSafeAndToxicWithDifferentTtls() {
        cache.store("safe content", "model", "v1", new AiModerationResult(0.1, "SAFE", "ok"));
        cache.store("toxic content", "model", "v1", new AiModerationResult(0.9, "TOXIC", "bad"));

        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofHours(24)));
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofDays(7)));
    }

    @Test
    void neverStoresAiError() {
        cache.store("content", "model", "v1", new AiModerationResult(0.9, "AI_ERROR", "down"));

        verify(valueOperations, never()).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void modelAndPromptVersionCreateIsolatedKeys() {
        when(valueOperations.get(anyString())).thenReturn(null);

        cache.find("same content", "model-a", "v1");
        cache.find("same content", "model-b", "v1");
        cache.find("same content", "model-b", "v2");

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations, org.mockito.Mockito.times(3)).get(keyCaptor.capture());
        List<String> keys = keyCaptor.getAllValues();
        assertEquals(3, keys.stream().distinct().count());
    }

    @Test
    void redisFailureFallsBackToCacheMissAndDoesNotBlockWrites() {
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(valueOperations).set(anyString(), anyString(), eq(Duration.ofHours(24)));

        assertTrue(cache.find("content", "model", "v1").isEmpty());
        assertDoesNotThrow(() -> cache.store(
                "content", "model", "v1", new AiModerationResult(0.1, "SAFE", "ok")));
    }

    @Test
    void invalidJsonIsEvictedAndTreatedAsMiss() {
        when(valueOperations.get(anyString())).thenReturn("not-json");

        assertTrue(cache.find("content", "model", "v1").isEmpty());
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void unsupportedCachedLabelIsEvictedAndTreatedAsMiss() throws Exception {
        String aiError = new ObjectMapper().writeValueAsString(
                new AiModerationResult(0.9, "AI_ERROR", "down"));
        when(valueOperations.get(anyString())).thenReturn(aiError);

        assertTrue(cache.find("content", "model", "v1").isEmpty());
        verify(redisTemplate).delete(anyString());
    }
}
