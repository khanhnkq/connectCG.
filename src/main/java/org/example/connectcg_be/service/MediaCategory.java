package org.example.connectcg_be.service;

import java.util.Arrays;

public enum MediaCategory {
    AVATAR("avatar", false),
    COVER("cover", false),
    POST("post", true),
    COMMENT("comment", false),
    GROUP("group", false),
    CHAT("chat", true);

    private final String path;
    private final boolean videoAllowed;

    MediaCategory(String path, boolean videoAllowed) {
        this.path = path;
        this.videoAllowed = videoAllowed;
    }

    public String path() {
        return path;
    }

    public boolean videoAllowed() {
        return videoAllowed;
    }

    public static MediaCategory from(String value) {
        if (value == null) {
            throw new MediaValidationException("Category là bắt buộc");
        }
        return Arrays.stream(values())
                .filter(category -> category.path.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new MediaValidationException("Category không hợp lệ"));
    }
}
