package org.example.connectcg_be.service;

import java.io.InputStream;

public interface ObjectStorageService {
    StoredObject store(InputStream inputStream, long size, String contentType, String objectKey);

    void delete(String objectKey);

    InputStream load(String objectKey);

    /**
     * Tên định danh của storage provider đang hoạt động.
     */
    default String getProviderName() {
        return "APPWRITE";
    }

    /**
     * Sinh URL thumbnail/preview trực tiếp nếu storage provider hỗ trợ (như Appwrite CDN).
     * Trả về null nếu provider không hỗ trợ để hệ thống fallback sang nén ngầm bằng Queue.
     */
    default String generateThumbnailUrl(String objectKey, int width, int height) {
        return null;
    }
}
