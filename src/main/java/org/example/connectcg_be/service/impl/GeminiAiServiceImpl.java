package org.example.connectcg_be.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.connectcg_be.cache.GeminiModerationCache;
import org.example.connectcg_be.dto.AiModerationResult;
import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.repository.MediaRepository;
import org.example.connectcg_be.service.AiModerationService;
import org.example.connectcg_be.service.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Primary
@Service
public class GeminiAiServiceImpl implements AiModerationService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAiServiceImpl.class);
    private static final Semaphore GEMINI_SEMAPHORE = new Semaphore(2);
    private static final int MAX_MODERATION_IMAGES = 6;
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024L; // 5MB

    record ResolvedMediaData(String mimeType, byte[] data, String sourceKey) {}

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

    @Autowired(required = false)
    private ObjectStorageService objectStorageService;

    @Autowired(required = false)
    private MediaRepository mediaRepository;

    @Autowired
    public GeminiAiServiceImpl(
            GeminiModerationCache moderationCache,
            @Autowired(required = false) ObjectStorageService objectStorageService,
            @Autowired(required = false) MediaRepository mediaRepository) {
        this(moderationCache, createDefaultRestTemplate(), new ObjectMapper(), objectStorageService, mediaRepository);
    }

    public GeminiAiServiceImpl(
            GeminiModerationCache moderationCache,
            RestTemplate restTemplate,
            ObjectMapper mapper) {
        this(moderationCache, restTemplate, mapper, null, null);
    }

    public GeminiAiServiceImpl(
            GeminiModerationCache moderationCache,
            RestTemplate restTemplate,
            ObjectMapper mapper,
            ObjectStorageService objectStorageService,
            MediaRepository mediaRepository) {
        this.moderationCache = moderationCache;
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.objectStorageService = objectStorageService;
        this.mediaRepository = mediaRepository;
    }

    private static RestTemplate createDefaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return new RestTemplate(factory);
    }

    @Override
    public AiModerationResult checkPostContent(String content) {
        return checkPostContent(content, Collections.emptyList());
    }

    @Override
    public AiModerationResult checkPostContent(String content, List<String> mediaUrls) {
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasMedia = mediaUrls != null && !mediaUrls.isEmpty();

        if (!hasContent && !hasMedia) {
            return new AiModerationResult(0.0, "SAFE", "Nội dung trống");
        }

        List<ResolvedMediaData> images = hasMedia ? resolveMediaImages(mediaUrls) : Collections.emptyList();
        if (!hasContent && images.isEmpty()) {
            if (hasMedia) {
                logger.info("MediaUrls provided but no inspectable image/thumbnail available yet.");
                return new AiModerationResult(0.0, "SAFE", "Chưa có hình ảnh/thumbnail khả dụng để kiểm duyệt");
            }
            return new AiModerationResult(0.0, "SAFE", "Nội dung trống");
        }

        List<String> cacheKeys = images.stream().map(ResolvedMediaData::sourceKey).toList();
        Optional<AiModerationResult> cached = cacheKeys.isEmpty()
                ? moderationCache.find(content, model, promptVersion)
                : moderationCache.find(content, cacheKeys, model, promptVersion);

        if (cached.isPresent()) {
            return cached.get();
        }

        // Nếu chưa cấu hình API Key thật thì bỏ qua an toàn để không chặn người dùng
        if (!isApiKeyConfigured()) {
            logger.warn("⚠️ Gemini API Key chưa được cấu hình. Bỏ qua kiểm duyệt AI tự động.");
            return new AiModerationResult(0.0, "SAFE", "Bỏ qua kiểm duyệt AI (chưa cấu hình API Key)");
        }

        AiModerationResult result = requestModeration(content, images);
        if (!"AI_ERROR".equals(result.getLabel())) {
            if (cacheKeys.isEmpty()) {
                moderationCache.store(content, model, promptVersion, result);
            } else {
                moderationCache.store(content, cacheKeys, model, promptVersion, result);
            }
        }
        return result;
    }

    private boolean isApiKeyConfigured() {
        return apiKey != null
                && !apiKey.trim().isEmpty()
                && !apiKey.contains("dummy")
                && !apiKey.contains("replace-with");
    }

    private AiModerationResult requestModeration(String content, List<ResolvedMediaData> images) {
        try {
            if (!GEMINI_SEMAPHORE.tryAcquire(1, TimeUnit.SECONDS)) {
                logger.warn("⚠️ Gemini AI semaphore saturated, marking as AI_ERROR");
                return new AiModerationResult(0.9, "AI_ERROR", "Hệ thống quá tải - Cần duyệt thủ công");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("⚠️ Interrupted while waiting for Gemini semaphore");
            return new AiModerationResult(0.9, "AI_ERROR", "Bị gián đoạn khi kiểm duyệt AI");
        }

        try {
            String url = String.format("%s/%s:generateContent?key=%s", apiUrl, model, apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildRequestBody(content, images), jsonHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseResponse(response.getBody());
            }
        } catch (Exception e) {
            logger.error("❌ Gemini API call failed: {}", e.getMessage(), e);
        } finally {
            GEMINI_SEMAPHORE.release();
        }

        return new AiModerationResult(0.9, "AI_ERROR", "Lỗi kết nối Gemini AI - Cần duyệt thủ công");
    }

    private Map<String, Object> buildRequestBody(String content, List<ResolvedMediaData> images) {
        List<Map<String, Object>> parts = new ArrayList<>();

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", buildPrompt(content, images != null ? images.size() : 0));
        parts.add(textPart);

        if (images != null) {
            for (ResolvedMediaData img : images) {
                Map<String, Object> inlineData = new HashMap<>();
                inlineData.put("mimeType", img.mimeType());
                inlineData.put("data", Base64.getEncoder().encodeToString(img.data()));

                Map<String, Object> part = new HashMap<>();
                part.put("inlineData", inlineData);
                parts.add(part);
            }
        }

        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("parts", parts);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.1);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", Collections.singletonList(contentMap));
        requestBody.put("generationConfig", generationConfig);
        return requestBody;
    }

    private String buildPrompt(String content, int imageCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là hệ thống kiểm duyệt nội dung mạng xã hội tiếng Việt.\n");
        sb.append("Hãy phân tích toàn diện nội dung bài viết bao gồm cả văn bản và tất cả hình ảnh/thumbnail đính kèm.\n");
        sb.append("Kiểm tra xem nội dung bài viết và các hình ảnh/thumbnail có vi phạm các tiêu chuẩn cộng đồng sau hay không:\n");
        sb.append("- Ngôn từ thù địch, xúc phạm, lăng mạ, quấy rối, công kích cá nhân.\n");
        sb.append("- Hình ảnh hoặc nội dung khiêu dâm, đồi trụy, 18+, khỏa thân phản cảm.\n");
        sb.append("- Bạo lực đẫm máu, kinh dị, tự hại, vũ khí trái phép, đe dọa.\n");
        sb.append("- Chất cấm, ma túy, cờ bạc, lừa đảo hoặc vi phạm pháp luật.\n\n");
        sb.append("Chỉ trả về DUY NHẤT 1 JSON object có định dạng chính xác:\n");
        sb.append("{\"label\": \"SAFE\" hoặc \"TOXIC\", \"reason\": \"giải thích ngắn gọn lý do bằng tiếng Việt\"}\n\n");
        if (content != null && !content.trim().isEmpty()) {
            sb.append("Nội dung văn bản cần kiểm duyệt: ").append(content).append("\n");
        } else {
            sb.append("Nội dung văn bản: (Bài viết không có nội dung chữ, vui lòng duyệt các hình ảnh/thumbnail đính kèm)\n");
        }
        if (imageCount > 0) {
            sb.append("Số lượng hình ảnh/thumbnail đính kèm cần kiểm duyệt: ").append(imageCount).append(" ảnh/thumbnail.\n");
        }
        return sb.toString();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private AiModerationResult parseResponse(String responseBody) throws Exception {
        JsonNode rootNode = mapper.readTree(responseBody);

        JsonNode promptFeedback = rootNode.path("promptFeedback");
        if (promptFeedback.has("blockReason") && !promptFeedback.path("blockReason").asText().isBlank()) {
            String blockReason = promptFeedback.path("blockReason").asText();
            logger.warn("⚠️ Gemini AI blocked prompt due to safety: {}", blockReason);
            return new AiModerationResult(0.9, "TOXIC", "Nội dung hoặc hình ảnh bị bộ lọc an toàn AI chặn: " + blockReason);
        }

        JsonNode candidates = rootNode.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini response has no candidates");
        }

        JsonNode candidate = candidates.get(0);
        String finishReason = candidate.path("finishReason").asText();
        if ("SAFETY".equalsIgnoreCase(finishReason)) {
            logger.warn("⚠️ Gemini candidate blocked by safety filter");
            return new AiModerationResult(0.9, "TOXIC", "Nội dung hoặc hình ảnh vi phạm bộ lọc an toàn của AI");
        }

        String rawText = candidate.path("content").path("parts").get(0).path("text").asText();
        String cleanedJson = rawText.replace("```json", "").replace("```", "").trim();
        JsonNode resultNode = mapper.readTree(cleanedJson);

        if (!resultNode.has("label") || resultNode.path("label").asText().isBlank()) {
            throw new IllegalStateException("Gemini response missing required 'label' field");
        }
        if (!resultNode.has("reason") || resultNode.path("reason").asText().isBlank()) {
            throw new IllegalStateException("Gemini response missing required 'reason' field");
        }

        String label = resultNode.path("label").asText().toUpperCase();
        if (!"SAFE".equals(label) && !"TOXIC".equals(label)) {
            throw new IllegalStateException("Unknown label value: " + label);
        }

        String reason = resultNode.path("reason").asText();
        boolean isSafe = "SAFE".equals(label);
        AiModerationResult result = new AiModerationResult(isSafe ? 0.1 : 0.9, isSafe ? "SAFE" : "TOXIC", reason);
        logger.info("🤖 Gemini AI Moderation: label={}, reason={}", result.getLabel(), reason);
        return result;
    }

    private List<ResolvedMediaData> resolveMediaImages(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return Collections.emptyList();
        }

        List<ResolvedMediaData> resolved = new ArrayList<>();
        int count = 0;
        for (String url : mediaUrls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            if (count >= MAX_MODERATION_IMAGES) {
                logger.info("Cap of {} media items reached for AI moderation", MAX_MODERATION_IMAGES);
                break;
            }

            try {
                ResolvedMediaData mediaData = loadSingleMedia(url.trim());
                if (mediaData != null && mediaData.data().length > 0) {
                    resolved.add(mediaData);
                    count++;
                }
            } catch (Exception e) {
                logger.warn("Could not load media [{}] for AI moderation: {}", url, e.getMessage());
            }
        }
        return resolved;
    }

    private ResolvedMediaData loadSingleMedia(String urlOrKey) {
        if (urlOrKey == null || urlOrKey.isBlank()) {
            return null;
        }

        // 1. Kiểm tra Media entity trong database
        if (mediaRepository != null) {
            Optional<Media> optMedia = mediaRepository.findFirstByUrlAndIsDeletedFalse(urlOrKey);
            if (optMedia.isPresent()) {
                Media m = optMedia.get();
                if ("VIDEO".equalsIgnoreCase(m.getType())) {
                    // Video: duyệt thumbnail của video
                    String thumbUrl = m.getThumbnailUrl();
                    if (thumbUrl != null && !thumbUrl.isBlank()) {
                        return loadSingleMedia(thumbUrl);
                    }
                    logger.info("Video media id [{}] has no thumbnail yet, skipping direct video inspection", m.getId());
                    return null;
                } else if ("IMAGE".equalsIgnoreCase(m.getType())) {
                    if (m.getObjectKey() != null && objectStorageService != null) {
                        try (InputStream is = objectStorageService.load(m.getObjectKey())) {
                            byte[] data = is.readAllBytes();
                            if (data.length <= MAX_IMAGE_BYTES) {
                                String mime = m.getContentType() != null ? m.getContentType() : detectMimeType(m.getObjectKey());
                                return new ResolvedMediaData(mime, data, m.getObjectKey());
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to load objectKey [{}] from storage: {}", m.getObjectKey(), e.getMessage());
                        }
                    }
                }
            }
        }

        // 2. Trích xuất objectKey nếu URL chứa format /media/view/ hoặc /connect-media/
        String extractedKey = extractObjectKeyFromUrl(urlOrKey);
        if (extractedKey != null && objectStorageService != null) {
            if (isVideoExtension(extractedKey)) {
                logger.info("Extracted objectKey is video [{}], skipping direct video", extractedKey);
                return null;
            }
            try (InputStream is = objectStorageService.load(extractedKey)) {
                byte[] data = is.readAllBytes();
                if (data.length <= MAX_IMAGE_BYTES) {
                    String mime = detectMimeType(extractedKey);
                    return new ResolvedMediaData(mime, data, extractedKey);
                }
            } catch (Exception e) {
                logger.debug("Failed to load extracted objectKey [{}] from storage: {}", extractedKey, e.getMessage());
            }
        }

        // 3. Nếu là objectKey trực tiếp trong storage (không phải HTTP URL)
        if (!urlOrKey.startsWith("http://") && !urlOrKey.startsWith("https://") && objectStorageService != null) {
            if (isVideoExtension(urlOrKey)) {
                return null;
            }
            try (InputStream is = objectStorageService.load(urlOrKey)) {
                byte[] data = is.readAllBytes();
                if (data.length <= MAX_IMAGE_BYTES) {
                    String mime = detectMimeType(urlOrKey);
                    return new ResolvedMediaData(mime, data, urlOrKey);
                }
            } catch (Exception e) {
                logger.debug("Failed to load key [{}] from storage: {}", urlOrKey, e.getMessage());
            }
        }

        // 4. Nếu là HTTP/HTTPS URL, fallback tải qua restTemplate
        if (urlOrKey.startsWith("http://") || urlOrKey.startsWith("https://")) {
            if (isVideoExtension(urlOrKey)) {
                return null;
            }
            try {
                ResponseEntity<byte[]> response = restTemplate.exchange(
                        urlOrKey, HttpMethod.GET, null, byte[].class);
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    byte[] data = response.getBody();
                    if (data.length <= MAX_IMAGE_BYTES) {
                        MediaType contentType = response.getHeaders().getContentType();
                        String mime = (contentType != null && !MediaType.APPLICATION_OCTET_STREAM.equals(contentType))
                                ? contentType.toString()
                                : detectMimeType(urlOrKey);
                        return new ResolvedMediaData(mime, data, urlOrKey);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to download image from URL [{}]: {}", urlOrKey, e.getMessage());
            }
        }

        return null;
    }

    private String extractObjectKeyFromUrl(String url) {
        if (url == null) return null;
        if (url.contains("/media/view/")) {
            String key = url.substring(url.indexOf("/media/view/") + "/media/view/".length());
            if (key.startsWith("connect-media/")) {
                key = key.substring("connect-media/".length());
            }
            return key;
        }
        if (url.contains("/connect-media/")) {
            return url.substring(url.indexOf("/connect-media/") + "/connect-media/".length());
        }
        return null;
    }

    private boolean isVideoExtension(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi")
                || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".flv");
    }

    private String detectMimeType(String name) {
        if (name == null) return "image/jpeg";
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
