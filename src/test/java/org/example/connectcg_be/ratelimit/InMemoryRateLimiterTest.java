package org.example.connectcg_be.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryRateLimiterTest {
    @Test
    void rejectsAfterLimitAndResetsWhenWindowExpires() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1_000L);
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        for (int request = 0; request < RateLimitPolicy.LOGIN.limit(); request++) {
            assertTrue(limiter.acquire("login-key", RateLimitPolicy.LOGIN).allowed());
        }

        RateLimitDecision rejected = limiter.acquire("login-key", RateLimitPolicy.LOGIN);
        assertFalse(rejected.allowed());
        assertTrue(rejected.retryAfterSeconds() > 0);

        when(clock.millis()).thenReturn(1_000L + RateLimitPolicy.LOGIN.window().toMillis());
        assertTrue(limiter.acquire("login-key", RateLimitPolicy.LOGIN).allowed());
    }

    @Test
    void keepsSubjectsIsolated() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1_000L);
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        for (int request = 0; request <= RateLimitPolicy.FORGOT_PASSWORD.limit(); request++) {
            limiter.acquire("first-email", RateLimitPolicy.FORGOT_PASSWORD);
        }

        assertTrue(limiter.acquire("second-email", RateLimitPolicy.FORGOT_PASSWORD).allowed());
    }
}
