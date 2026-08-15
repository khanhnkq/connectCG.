package org.example.connectcg_be.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.connectcg_be.dto.AiModerationResult;
import org.example.connectcg_be.service.AiModerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public AiModerationResult checkPostContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new AiModerationResult(0.0, "SAFE", "Nội dung trống");
        }

        // Nếu chưa cấu hình API Key thật thì bỏ qua an toàn để không chặn người dùng
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("dummy") || apiKey.contains("replace-with")) {
            logger.warn("⚠️ Gemini API Key chưa được cấu hình. Bỏ qua kiểm duyệt AI tự động.");
            return new AiModerationResult(0.0, "SAFE", "Bỏ qua kiểm duyệt AI (chưa cấu hình API Key)");
        }

        try {
            String prompt = "Bạn là hệ thống kiểm duyệt nội dung mạng xã hội tiếng Việt. "
                    + "Hãy phân tích nội dung sau xem có chứa từ ngữ thô tục, độc hại, xúc phạm, lăng mạ, bạo lực hoặc vi phạm chuẩn mực hay không.\n"
                    + "Chỉ trả về duy nhất 1 JSON object có định dạng:\n"
                    + "{\"label\": \"SAFE\" hoặc \"TOXIC\", \"reason\": \"giải thích ngắn gọn bằng tiếng Việt\"}\n\n"
                    + "Nội dung cần phân tích: " + content;

            // Endpoint: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
            String url = String.format("%s/%s:generateContent?key=%s", apiUrl, model, apiKey);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Cấu trúc payload chuẩn của Google Gemini API
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);

            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("parts", Collections.singletonList(textPart));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("temperature", 0.1);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", Collections.singletonList(contentMap));
            requestBody.put("generationConfig", generationConfig);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode rootNode = mapper.readTree(response.getBody());

                JsonNode candidates = rootNode.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    String rawText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();

                    // Xóa markdown code block nếu có
                    String cleanedJson = rawText.replaceAll("```json", "").replaceAll("```", "").trim();

                    JsonNode resultNode = mapper.readTree(cleanedJson);
                    String label = resultNode.path("label").asText("SAFE").toUpperCase();
                    String reason = resultNode.path("reason").asText("Nội dung hợp lệ");

                    double score = "SAFE".equals(label) ? 0.1 : 0.9;
                    if (!"SAFE".equals(label)) {
                        label = "TOXIC";
                    }

                    logger.info("🤖 Gemini AI Moderation: label={}, reason={}", label, reason);
                    return new AiModerationResult(score, label, reason);
                }
            }
        } catch (Exception e) {
            logger.error("❌ Gemini API call failed: {}", e.getMessage(), e);
        }

        // Fail-safe: Khi có lỗi mạng/API
        return new AiModerationResult(0.9, "AI_ERROR", "Lỗi kết nối Gemini AI - Cần duyệt thủ công");
    }
}
