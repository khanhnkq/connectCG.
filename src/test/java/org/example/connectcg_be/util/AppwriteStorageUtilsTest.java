package org.example.connectcg_be.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppwriteStorageUtilsTest {

    private static final String APPWRITE_ID_REGEX = "^[a-zA-Z0-9][a-zA-Z0-9._-]{0,35}$";

    @Test
    @DisplayName("Ánh xạ chính xác các tiền tố category cho ConnectCG dựa trên UUID")
    void testCategoryPrefixMapping() {
        String uuid = "8d89e5a8-5690-4e35-a6fa-901a88b5774a";
        String hex = "8d89e5a856904e35a6fa901a88b5774a";

        assertEquals("av_" + hex, AppwriteStorageUtils.toAppwriteId("avatar/2026/10/" + uuid + ".jpg"));
        assertEquals("po_" + hex, AppwriteStorageUtils.toAppwriteId("post/2026/10/" + uuid + ".jpg"));
        assertEquals("cv_" + hex, AppwriteStorageUtils.toAppwriteId("cover/2026/10/" + uuid + ".jpg"));
        assertEquals("cm_" + hex, AppwriteStorageUtils.toAppwriteId("comment/2026/10/" + uuid + ".jpg"));
        assertEquals("gr_" + hex, AppwriteStorageUtils.toAppwriteId("group/2026/10/" + uuid + ".jpg"));
        assertEquals("ch_" + hex, AppwriteStorageUtils.toAppwriteId("chat/2026/10/" + uuid + ".png"));
        assertEquals("th_" + hex, AppwriteStorageUtils.toAppwriteId("avatar/thumbnails/2026/10/" + uuid + ".jpg"));
        assertEquals("th_" + hex, AppwriteStorageUtils.toAppwriteId("post/2026/10/thumb_" + uuid + ".jpg"));
    }

    @Test
    @DisplayName("Hàm toAppwriteId phải tuyệt đối tất định (Deterministic)")
    void testDeterministicConsistency() {
        String key = "post/2026/10/c0ffee11-2233-4455-6677-8899aabbccdd.mp4";
        String first = AppwriteStorageUtils.toAppwriteId(key);

        for (int i = 0; i < 100; i++) {
            assertEquals(first, AppwriteStorageUtils.toAppwriteId(key));
        }
    }

    @Test
    @DisplayName("Mọi Appwrite fileId sinh ra đều tuân thủ độ dài <= 36 chars và regex Appwrite")
    void testAppwriteIdConstraints() {
        String[] sampleKeys = {
                "avatar/2026/10/8d89e5a8-5690-4e35-a6fa-901a88b5774a.jpg",
                "post/thumbnails/2026/10/8d89e5a8-5690-4e35-a6fa-901a88b5774a.jpg",
                "chat/2026/10/8d89e5a8-5690-4e35-a6fa-901a88b5774a.png",
                "non-uuid-path/images/test-file.png",
                "deeply/nested/arbitrary/path/without/uuid/sample.bin",
                "already-valid-standalone-id-123",
                "",
                null
        };

        for (String key : sampleKeys) {
            String fileId = AppwriteStorageUtils.toAppwriteId(key);
            assertNotNull(fileId);
            assertTrue(fileId.length() <= 36, "fileId length must be <= 36 chars: " + fileId);
            assertTrue(fileId.matches(APPWRITE_ID_REGEX), "fileId must match Appwrite regex: " + fileId);
            assertFalse(fileId.contains("/"), "fileId must not contain slashes: " + fileId);
        }
    }

    @Test
    @DisplayName("Giữ nguyên ID nếu đã là standalone ID hợp lệ không chứa dấu '/'")
    void testStandaloneValidId() {
        String standalone = "valid_file-id.123";
        assertEquals(standalone, AppwriteStorageUtils.toAppwriteId(standalone));
    }

    @Test
    @DisplayName("Fallback sha256 cho key không chứa UUID")
    void testFallbackHash() {
        String key = "custom/folder/without-uuid/test.png";
        String fileId = AppwriteStorageUtils.toAppwriteId(key);
        assertTrue(fileId.startsWith("f_"));
        assertEquals(35, fileId.length());
        assertTrue(fileId.matches(APPWRITE_ID_REGEX));
    }

    @Test
    @DisplayName("toSafeFilename bảo đảm có đuôi extension phù hợp chuẩn")
    void testSafeFilename() {
        assertEquals("sample.jpg", AppwriteStorageUtils.toSafeFilename("post/2026/10/sample.jpg", "image/jpeg"));
        assertEquals("clip.mp4", AppwriteStorageUtils.toSafeFilename("post/2026/10/clip.mp4", "video/mp4"));
        assertEquals("icon.png", AppwriteStorageUtils.toSafeFilename("avatar/icon.png", "image/png"));
        assertEquals("image.webp", AppwriteStorageUtils.toSafeFilename("post/image.webp", "image/webp"));

        // Không có extension nhưng có MIME type
        assertEquals("clip.mp4", AppwriteStorageUtils.toSafeFilename("post/clip", "video/mp4"));
        assertEquals("logo.png", AppwriteStorageUtils.toSafeFilename("post/logo", "image/png"));
        assertEquals("art.webp", AppwriteStorageUtils.toSafeFilename("post/art", "image/webp"));
        assertEquals("photo.jpg", AppwriteStorageUtils.toSafeFilename("post/photo", "image/jpeg"));

        // Fallback
        assertEquals("file.bin", AppwriteStorageUtils.toSafeFilename(null, null));
    }
}
