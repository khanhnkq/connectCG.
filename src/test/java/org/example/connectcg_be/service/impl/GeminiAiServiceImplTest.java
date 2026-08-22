package org.example.connectcg_be.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.connectcg_be.cache.GeminiModerationCache;
import org.example.connectcg_be.dto.AiModerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiAiServiceImplTest {
    private GeminiModerationCache cache;
    private RestTemplate restTemplate;
    private GeminiAiServiceImpl service;

    @BeforeEach
    void setUp() {
        cache = mock(GeminiModerationCache.class);
        restTemplate = mock(RestTemplate.class);
        service = new GeminiAiServiceImpl(cache, restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://gemini.test/models");
        ReflectionTestUtils.setField(service, "model", "gemini-test");
        ReflectionTestUtils.setField(service, "promptVersion", "v1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatedContentCallsGeminiOnlyOnce() {
        String content = "Nội dung cần kiểm duyệt";
        AiModerationResult cachedResult = new AiModerationResult(0.1, "SAFE", "Hợp lệ");
        when(cache.find(content, "gemini-test", "v1"))
                .thenReturn(Optional.empty(), Optional.of(cachedResult));
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiResponse("SAFE", "Hợp lệ")));

        AiModerationResult first = service.checkPostContent(content);
        AiModerationResult second = service.checkPostContent(content);

        assertEquals("SAFE", first.getLabel());
        assertEquals("SAFE", second.getLabel());
        verify(restTemplate, times(1)).exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        verify(cache).store(eq(content), eq("gemini-test"), eq("v1"), any(AiModerationResult.class));
    }

    @Test
    void cacheHitWorksWithoutConfiguredApiKey() {
        AiModerationResult cachedResult = new AiModerationResult(0.9, "TOXIC", "Vi phạm");
        when(cache.find("cached", "gemini-test", "v1")).thenReturn(Optional.of(cachedResult));
        ReflectionTestUtils.setField(service, "apiKey", "");

        AiModerationResult result = service.checkPostContent("cached");

        assertEquals("TOXIC", result.getLabel());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    void missingApiKeyBypassIsNotStored() {
        when(cache.find("uncached", "gemini-test", "v1")).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(service, "apiKey", "");

        AiModerationResult result = service.checkPostContent("uncached");

        assertEquals("SAFE", result.getLabel());
        verify(cache, never()).store(anyString(), anyString(), anyString(), any());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    private String geminiResponse(String label, String reason) {
        return """
                {"candidates":[{"content":{"parts":[{"text":"{\\"label\\":\\"%s\\",\\"reason\\":\\"%s\\"}"}]}}]}
                """.formatted(label, reason);
    }
}
