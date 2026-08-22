package org.example.connectcg_be.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.connectcg_be.dto.AiModerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GeminiModerationCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiModerationCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String environment;
    private final boolean enabled;
    private final Duration safeTtl;
    private final Duration toxicTtl;
    private final AtomicBoolean redisUnavailable = new AtomicBoolean(false);

    public GeminiModerationCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.environment:local}") String environment,
            @Value("${app.ai-moderation-cache.enabled:true}") boolean enabled,
            @Value("${app.ai-moderation-cache.safe-ttl:24h}") Duration safeTtl,
            @Value("${app.ai-moderation-cache.toxic-ttl:7d}") Duration toxicTtl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.environment = environment.trim().toLowerCase(Locale.ROOT);
        this.enabled = enabled;
        this.safeTtl = safeTtl;
        this.toxicTtl = toxicTtl;
    }

    public Optional<AiModerationResult> find(String content, String model, String promptVersion) {
        if (!enabled) {
            return Optional.empty();
        }

        String key = buildKey(content, model, promptVersion);
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                markAvailable();
                return Optional.empty();
            }
            AiModerationResult result = objectMapper.readValue(cached, AiModerationResult.class);
            if (ttlFor(result) == null) {
                LOGGER.warn("Unsupported Gemini moderation cache entry; evicting key");
                evict(key);
                return Optional.empty();
            }
            markAvailable();
            return Optional.of(result);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Invalid Gemini moderation cache entry; evicting key");
            evict(key);
            return Optional.empty();
        } catch (DataAccessException exception) {
            markUnavailable("read", exception);
            return Optional.empty();
        }
    }

    public void store(String content, String model, String promptVersion, AiModerationResult result) {
        Duration ttl = ttlFor(result);
        if (!enabled || ttl == null) {
            return;
        }

        try {
            String key = buildKey(content, model, promptVersion);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), ttl);
            markAvailable();
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Could not serialize Gemini moderation cache result: {}", exception.getMessage());
        } catch (DataAccessException exception) {
            markUnavailable("write", exception);
        }
    }

    private Duration ttlFor(AiModerationResult result) {
        if (result == null || result.getLabel() == null) {
            return null;
        }
        return switch (result.getLabel().toUpperCase(Locale.ROOT)) {
            case "SAFE" -> safeTtl;
            case "TOXIC" -> toxicTtl;
            default -> null;
        };
    }

    private String buildKey(String content, String model, String promptVersion) {
        String material = normalizeContent(content) + '\0'
                + normalizeMetadata(model) + '\0'
                + normalizeMetadata(promptVersion);
        return "connect:%s:ai-moderation:v1:%s".formatted(environment, sha256(material));
    }

    private String normalizeContent(String content) {
        return Normalizer.normalize(content == null ? "" : content, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("\\s+", " ");
    }

    private String normalizeMetadata(String value) {
        return value == null ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException exception) {
            markUnavailable("eviction", exception);
        }
    }

    private void markUnavailable(String operation, DataAccessException exception) {
        if (redisUnavailable.compareAndSet(false, true)) {
            LOGGER.warn("Gemini moderation cache {} unavailable: {}", operation, exception.getMessage());
        }
    }

    private void markAvailable() {
        if (redisUnavailable.compareAndSet(true, false)) {
            LOGGER.info("Gemini moderation cache available again");
        }
    }
}
