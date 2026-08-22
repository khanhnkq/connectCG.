package org.example.connectcg_be.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void acceptsRequestWithinRedisLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        InMemoryRateLimiter fallback = mock(InMemoryRateLimiter.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(List.of(5L, 600_000L));
        RateLimitService service = new RateLimitService(redisTemplate, fallback, "test", true);

        assertDoesNotThrow(() -> service.check(RateLimitPolicy.LOGIN, "127.0.0.1|user"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsRetryAfterWhenRedisLimitIsExceeded() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        InMemoryRateLimiter fallback = mock(InMemoryRateLimiter.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(List.of(6L, 90_001L));
        RateLimitService service = new RateLimitService(redisTemplate, fallback, "test", true);

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> service.check(RateLimitPolicy.LOGIN, "127.0.0.1|user"));

        assertEquals(91, exception.getRetryAfterSeconds());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToMemoryWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        InMemoryRateLimiter fallback = mock(InMemoryRateLimiter.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));
        when(fallback.acquire(anyString(), eq(RateLimitPolicy.LOGIN)))
                .thenReturn(RateLimitDecision.reject(30));
        RateLimitService service = new RateLimitService(redisTemplate, fallback, "test", true);

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> service.check(RateLimitPolicy.LOGIN, "127.0.0.1|user"));

        assertEquals(30, exception.getRetryAfterSeconds());
        verify(fallback).acquire(anyString(), eq(RateLimitPolicy.LOGIN));
    }
}
