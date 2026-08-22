package org.example.connectcg_be.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryRateLimiter {
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public RateLimitDecision acquire(String key, RateLimitPolicy policy) {
        long now = clock.millis();
        Window window = windows.compute(key, (ignored, current) -> nextWindow(current, policy, now));
        if (window.count() <= policy.limit()) {
            return RateLimitDecision.permit();
        }
        return RateLimitDecision.reject(toSeconds(window.expiresAtMillis() - now));
    }

    private Window nextWindow(Window current, RateLimitPolicy policy, long now) {
        if (current == null || current.expiresAtMillis() <= now) {
            return new Window(1, now + policy.window().toMillis());
        }
        return new Window(current.count() + 1, current.expiresAtMillis());
    }

    private long toSeconds(long millis) {
        return Math.max(1, (millis + 999) / 1000);
    }

    private record Window(int count, long expiresAtMillis) {
    }
}
