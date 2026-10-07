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
import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.repository.MediaRepository;
import org.example.connectcg_be.service.ObjectStorageService;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiAiServiceImplTest {
    private GeminiModerationCache cache;
    private RestTemplate restTemplate;
    private ObjectStorageService objectStorageService;
    private MediaRepository mediaRepository;
    private GeminiAiServiceImpl service;

    @BeforeEach
    void setUp() {
        cache = mock(GeminiModerationCache.class);
        restTemplate = mock(RestTemplate.class);
        objectStorageService = mock(ObjectStorageService.class);
        mediaRepository = mock(MediaRepository.class);
        service = new GeminiAiServiceImpl(cache, restTemplate, new ObjectMapper(), objectStorageService, mediaRepository);
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

    @Test
    @SuppressWarnings("unchecked")
    void emptyResponseResultsInAiErrorAndNotCached() {
        when(cache.find("empty-test", "gemini-test", "v1")).thenReturn(Optional.empty());
        String emptyJsonPayload = """
                {"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}
                """;
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(emptyJsonPayload));

        AiModerationResult result = service.checkPostContent("empty-test");

        assertEquals("AI_ERROR", result.getLabel());
        assertEquals(0.9, result.getScore());
        verify(cache, never()).store(anyString(), anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingReasonResultsInAiError() {
        when(cache.find("missing-reason", "gemini-test", "v1")).thenReturn(Optional.empty());
        String jsonPayload = """
                {"candidates":[{"content":{"parts":[{"text":"{\\"label\\":\\"SAFE\\"}"}]}}]}
                """;
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonPayload));

        AiModerationResult result = service.checkPostContent("missing-reason");

        assertEquals("AI_ERROR", result.getLabel());
        verify(cache, never()).store(anyString(), anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownLabelValueResultsInAiError() {
        when(cache.find("unknown-label", "gemini-test", "v1")).thenReturn(Optional.empty());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiResponse("UNKNOWN_VAL", "Lý do")));

        AiModerationResult result = service.checkPostContent("unknown-label");

        assertEquals("AI_ERROR", result.getLabel());
        verify(cache, never()).store(anyString(), anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void restTemplateExceptionReturnsAiErrorAndNotCached() {
        when(cache.find("timeout-test", "gemini-test", "v1")).thenReturn(Optional.empty());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Read timed out"));

        AiModerationResult result = service.checkPostContent("timeout-test");

        assertEquals("AI_ERROR", result.getLabel());
        assertEquals(0.9, result.getScore());
        verify(cache, never()).store(anyString(), anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void moderatesPostWithImageAndTextMultimodal() {
        String content = "Ảnh phong cảnh đẹp";
        String imageUrl = "https://connect-api.quizken.com/api/v1/media/view/post/2026-10/landscape.jpg";
        byte[] fakeImageBytes = new byte[]{1, 2, 3, 4, 5};

        Media media = new Media();
        media.setId(10);
        media.setUrl(imageUrl);
        media.setType("IMAGE");
        media.setObjectKey("post/2026-10/landscape.jpg");
        media.setContentType("image/jpeg");

        when(mediaRepository.findFirstByUrlAndIsDeletedFalse(imageUrl)).thenReturn(Optional.of(media));
        when(objectStorageService.load("post/2026-10/landscape.jpg")).thenReturn(new ByteArrayInputStream(fakeImageBytes));
        when(cache.find(eq(content), eq(List.of("post/2026-10/landscape.jpg")), eq("gemini-test"), eq("v1")))
                .thenReturn(Optional.empty());

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiResponse("SAFE", "Hình ảnh phong cảnh hợp lệ")));

        AiModerationResult result = service.checkPostContent(content, List.of(imageUrl));

        assertEquals("SAFE", result.getLabel());
        assertEquals("Hình ảnh phong cảnh hợp lệ", result.getReason());
        assertEquals(0.1, result.getScore());

        // Verify request payload contains both prompt text and inlineData
        HttpEntity<Map<String, Object>> capturedEntity = captor.getValue();
        assertNotNull(capturedEntity);
        Map<String, Object> body = capturedEntity.getBody();
        assertNotNull(body);
        List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) contents.get(0).get("parts");
        assertEquals(2, parts.size()); // 1 text + 1 image

        assertTrue(parts.get(0).containsKey("text"));
        assertTrue(parts.get(1).containsKey("inlineData"));
        Map<String, Object> inlineData = (Map<String, Object>) parts.get(1).get("inlineData");
        assertEquals("image/jpeg", inlineData.get("mimeType"));
        assertNotNull(inlineData.get("data"));

        verify(cache).store(eq(content), eq(List.of("post/2026-10/landscape.jpg")), eq("gemini-test"), eq("v1"), any(AiModerationResult.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void moderatesPostWithVideoResolvesThumbnailFromMedia() {
        String content = "Video clip hài hước";
        String videoUrl = "https://connect-api.quizken.com/api/v1/media/view/post/2026-10/funny.mp4";
        String thumbUrl = "https://connect-api.quizken.com/api/v1/media/view/post/2026-10/thumb_funny.jpg";
        byte[] fakeThumbBytes = new byte[]{10, 20, 30};

        Media videoMedia = new Media();
        videoMedia.setId(20);
        videoMedia.setUrl(videoUrl);
        videoMedia.setType("VIDEO");
        videoMedia.setThumbnailUrl(thumbUrl);

        when(mediaRepository.findFirstByUrlAndIsDeletedFalse(videoUrl)).thenReturn(Optional.of(videoMedia));
        when(objectStorageService.load("post/2026-10/thumb_funny.jpg")).thenReturn(new ByteArrayInputStream(fakeThumbBytes));
        when(cache.find(eq(content), eq(List.of("post/2026-10/thumb_funny.jpg")), eq("gemini-test"), eq("v1")))
                .thenReturn(Optional.empty());

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiResponse("SAFE", "Thumbnail video hợp lệ")));

        AiModerationResult result = service.checkPostContent(content, List.of(videoUrl));

        assertEquals("SAFE", result.getLabel());
        assertEquals(0.1, result.getScore());

        HttpEntity<Map<String, Object>> captured = captor.getValue();
        List<Map<String, Object>> contents = (List<Map<String, Object>>) captured.getBody().get("contents");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) contents.get(0).get("parts");
        assertEquals(2, parts.size()); // 1 text + 1 thumbnail
    }

    @Test
    @SuppressWarnings("unchecked")
    void moderatesPostWithImageOnlyNoText() {
        String imageUrl = "https://connect-api.quizken.com/api/v1/media/view/post/2026-10/photo.png";
        byte[] fakeImageBytes = new byte[]{99, 88, 77};

        when(objectStorageService.load("post/2026-10/photo.png")).thenReturn(new ByteArrayInputStream(fakeImageBytes));
        when(cache.find(eq(""), eq(List.of("post/2026-10/photo.png")), eq("gemini-test"), eq("v1")))
                .thenReturn(Optional.empty());

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiResponse("SAFE", "Hình ảnh sạch")));

        AiModerationResult result = service.checkPostContent("", List.of(imageUrl));

        assertEquals("SAFE", result.getLabel());
        assertEquals(0.1, result.getScore());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlesSafetyFilterBlockedResponseFromGemini() {
        String content = "Nội dung cực kỳ độc hại";
        String safetyBlockedJson = """
                {"candidates":[{"finishReason":"SAFETY","content":{"parts":[]}}]}
                """;
        when(cache.find(content, "gemini-test", "v1")).thenReturn(Optional.empty());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(safetyBlockedJson));

        AiModerationResult result = service.checkPostContent(content);

        assertEquals("TOXIC", result.getLabel());
        assertEquals(0.9, result.getScore());
        assertTrue(result.getReason().contains("bộ lọc an toàn"));
    }

    private String geminiResponse(String label, String reason) {
        return """
                {"candidates":[{"content":{"parts":[{"text":"{\\"label\\":\\"%s\\",\\"reason\\":\\"%s\\"}"}]}}]}
                """.formatted(label, reason);
    }
}

