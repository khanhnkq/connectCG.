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

    public boolean isPublic() {
        return this == AVATAR || this == COVER;
    }

    public static MediaCategory fromObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        int slashIndex = objectKey.indexOf('/');
        String prefix = slashIndex > 0 ? objectKey.substring(0, slashIndex) : objectKey;
        for (MediaCategory cat : values()) {
            if (cat.path.equalsIgnoreCase(prefix)) {
                return cat;
            }
        }
        return null;
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
