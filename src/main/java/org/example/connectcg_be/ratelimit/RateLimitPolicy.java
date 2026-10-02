package org.example.connectcg_be.ratelimit;

import java.time.Duration;

public enum RateLimitPolicy {
    LOGIN("login", 5, Duration.ofMinutes(10)),
    LOGIN_IP("login-ip", 20, Duration.ofMinutes(10)),
    REGISTER("register", 5, Duration.ofHours(1)),
    FORGOT_PASSWORD("forgot-password", 3, Duration.ofMinutes(30)),
    RESET_PASSWORD("reset-password", 5, Duration.ofMinutes(15)),
    AI_POST("ai-post", 20, Duration.ofHours(1)),
    MEDIA_UPLOAD("media-upload", 60, Duration.ofHours(1)),
    WEBSOCKET_TYPING("websocket-typing", 5, Duration.ofSeconds(1));

    private final String key;
    private final int limit;
    private final Duration window;

    RateLimitPolicy(String key, int limit, Duration window) {
        this.key = key;
        this.limit = limit;
        this.window = window;
    }

    public String key() {
        return key;
    }

    public int limit() {
        return limit;
    }

    public Duration window() {
        return window;
    }
}
