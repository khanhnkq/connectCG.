package org.example.connectcg_be.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.connectcg_be.cache.GeminiModerationCache;
import org.example.connectcg_be.dto.AiModerationResult;
import org.example.connectcg_be.service.AiModerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Primary
@Service
public class GeminiAiServiceImpl implements AiModerationService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAiServiceImpl.class);

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String apiUrl;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String model;

    @Value("${gemini.prompt.version:v1}")
    private String promptVersion;

    private final GeminiModerationCache moderationCache;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    @Autowired
    public GeminiAiServiceImpl(GeminiModerationCache moderationCache) {
        this(moderationCache, new RestTemplate(), new ObjectMapper());
    }

    GeminiAiServiceImpl(
            GeminiModerationCache moderationCache,
            RestTemplate restTemplate,
            ObjectMapper mapper) {
        this.moderationCache = moderationCache;
        this.restTemplate = restTemplate;
        this.mapper = mapper;
    }

    @Override
    public AiModerationResult checkPostContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new AiModerationResult(0.0, "SAFE", "Nội dung trống");
        }

        Optional<AiModerationResult> cached = moderationCache.find(content, model, promptVersion);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Nếu chưa cấu hình API Key thật thì bỏ qua an toàn để không chặn người dùng
        if (!isApiKeyConfigured()) {
            logger.warn("⚠️ Gemini API Key chưa được cấu hình. Bỏ qua kiểm duyệt AI tự động.");
            return new AiModerationResult(0.0, "SAFE", "Bỏ qua kiểm duyệt AI (chưa cấu hình API Key)");
        }

        AiModerationResult result = requestModeration(content);
        moderationCache.store(content, model, promptVersion, result);
        return result;
    }

    private boolean isApiKeyConfigured() {
        return apiKey != null
                && !apiKey.trim().isEmpty()
                && !apiKey.contains("dummy")
                && !apiKey.contains("replace-with");
    }

    private AiModerationResult requestModeration(String content) {
        try {
            String url = String.format("%s/%s:generateContent?key=%s", apiUrl, model, apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildRequestBody(content), jsonHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseResponse(response.getBody());
            }
        } catch (Exception e) {
            logger.error("❌ Gemini API call failed: {}", e.getMessage(), e);
        }

        return new AiModerationResult(0.9, "AI_ERROR", "Lỗi kết nối Gemini AI - Cần duyệt thủ công");
    }

    private Map<String, Object> buildRequestBody(String content) {
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", buildPrompt(content));

        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("parts", Collections.singletonList(textPart));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.1);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", Collections.singletonList(contentMap));
        requestBody.put("generationConfig", generationConfig);
        return requestBody;
    }

    private String buildPrompt(String content) {
        return "Bạn là hệ thống kiểm duyệt nội dung mạng xã hội tiếng Việt. "
                + "Hãy phân tích nội dung sau xem có chứa từ ngữ thô tục, độc hại, xúc phạm, lăng mạ, bạo lực hoặc vi phạm chuẩn mực hay không.\n"
                + "Chỉ trả về duy nhất 1 JSON object có định dạng:\n"
                + "{\"label\": \"SAFE\" hoặc \"TOXIC\", \"reason\": \"giải thích ngắn gọn bằng tiếng Việt\"}\n\n"
                + "Nội dung cần phân tích: " + content;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private AiModerationResult parseResponse(String responseBody) throws Exception {
        JsonNode candidates = mapper.readTree(responseBody).path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini response has no candidates");
        }

        String rawText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
        String cleanedJson = rawText.replace("```json", "").replace("```", "").trim();
        JsonNode resultNode = mapper.readTree(cleanedJson);
        String label = resultNode.path("label").asText("SAFE").toUpperCase();
        String reason = resultNode.path("reason").asText("Nội dung hợp lệ");
        boolean isSafe = "SAFE".equals(label);
        AiModerationResult result = new AiModerationResult(isSafe ? 0.1 : 0.9, isSafe ? "SAFE" : "TOXIC", reason);
        logger.info("🤖 Gemini AI Moderation: label={}, reason={}", result.getLabel(), reason);
        return result;
    }
}
