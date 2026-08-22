package org.example.connectcg_be.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RateLimitService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitService.class);
    private static final DefaultRedisScript<List> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            return {current, ttl}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final InMemoryRateLimiter fallbackLimiter;
    private final String environment;
    private final boolean enabled;
    private final AtomicBoolean redisUnavailable = new AtomicBoolean(false);

    public RateLimitService(
            StringRedisTemplate redisTemplate,
            InMemoryRateLimiter fallbackLimiter,
            @Value("${app.environment:local}") String environment,
            @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.fallbackLimiter = fallbackLimiter;
        this.environment = normalize(environment);
        this.enabled = enabled;
    }

    public void check(RateLimitPolicy policy, String subject) {
        if (!enabled) {
            return;
        }

        String key = buildKey(policy, subject);
        RateLimitDecision decision = acquire(key, policy);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }

    private RateLimitDecision acquire(String key, RateLimitPolicy policy) {
        try {
            List<?> result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(key),
                    Long.toString(policy.window().toMillis()));
            RateLimitDecision decision = toDecision(result, policy);
            if (redisUnavailable.compareAndSet(true, false)) {
                LOGGER.info("Redis rate limiter available again");
            }
            return decision;
        } catch (DataAccessException | IllegalStateException exception) {
            if (redisUnavailable.compareAndSet(false, true)) {
                LOGGER.warn("Redis rate limiter unavailable; using in-memory fallback", exception);
            }
            return fallbackLimiter.acquire(key, policy);
        }
    }

    private RateLimitDecision toDecision(List<?> result, RateLimitPolicy policy) {
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Redis rate-limit script returned an invalid result");
        }
        long count = ((Number) result.get(0)).longValue();
        long ttlMillis = ((Number) result.get(1)).longValue();
        if (count <= policy.limit()) {
            return RateLimitDecision.permit();
        }
        return RateLimitDecision.reject(Math.max(1, (ttlMillis + 999) / 1000));
    }

    private String buildKey(RateLimitPolicy policy, String subject) {
        return "connect:%s:rate-limit:v1:%s:%s".formatted(
                environment,
                policy.key(),
                sha256(normalize(subject)));
    }

    private String normalize(String value) {
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
}
