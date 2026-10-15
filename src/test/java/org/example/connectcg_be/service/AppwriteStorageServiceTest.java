package org.example.connectcg_be.service;

import org.example.connectcg_be.config.AppwriteStorageProperties;
import org.example.connectcg_be.service.impl.AppwriteStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AppwriteStorageServiceTest {

    private AppwriteStorageProperties properties;
    private MockRestServiceServer mockServer;
    private AppwriteStorageService service;

    @BeforeEach
    void setUp() {
        properties = new AppwriteStorageProperties();
        properties.setEndpoint("https://fra.cloud.appwrite.io/v1");
        properties.setProjectId("backend-storage-hub");
        properties.setBucketId("connect-storage");
        properties.setApiKey("test-secret-key");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getEndpoint())
                .defaultHeader("X-Appwrite-Project", properties.getProjectId())
                .defaultHeader("X-Appwrite-Key", properties.getApiKey());

        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        service = new AppwriteStorageService(properties, restClient);
    }

    @Test
    @DisplayName("Upload ảnh công khai (POST) gửi đúng headers, permissions và trả về Appwrite CDN View URL")
    void testStorePublicMediaPost() {
        String objectKey = "post/2026/10/8d89e5a8-5690-4e35-a6fa-901a88b5774a.jpg";
        byte[] fakeImage = "test-image-content".getBytes(StandardCharsets.UTF_8);

        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/connect-storage/files"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Appwrite-Project", "backend-storage-hub"))
                .andExpect(header("X-Appwrite-Key", "test-secret-key"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"$id\":\"po_8d89e5a856904e35a6fa901a88b5774a\"}"));

        StoredObject stored = service.store(new ByteArrayInputStream(fakeImage), fakeImage.length, "image/jpeg", objectKey);

        mockServer.verify();
        assertNotNull(stored);
        assertEquals("connect-storage", stored.bucket());
        assertEquals(objectKey, stored.objectKey());
        assertTrue(stored.url().startsWith("https://fra.cloud.appwrite.io/v1/storage/buckets/connect-storage/files/po_8d89e5a856904e35a6fa901a88b5774a/view?project=backend-storage-hub"));
    }

    @Test
    @DisplayName("Upload media riêng tư (CHAT) không cấp quyền public và trả về Backend Proxy URL")
    void testStorePrivateMediaChat() {
        String objectKey = "chat/2026/10/8d89e5a8-5690-4e35-a6fa-901a88b5774a.png";
        byte[] fakeImage = "private-chat-image".getBytes(StandardCharsets.UTF_8);

        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/connect-storage/files"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"$id\":\"ch_8d89e5a856904e35a6fa901a88b5774a\"}"));

        StoredObject stored = service.store(new ByteArrayInputStream(fakeImage), fakeImage.length, "image/png", objectKey);

        mockServer.verify();
        assertNotNull(stored);
        assertEquals("/api/v1/media/view/" + objectKey, stored.url());
    }

    @Test
    @DisplayName("Tải file nhị phân thành công qua load()")
    void testLoadMediaSuccess() throws Exception {
        String objectKey = "post/2026/10/8d89e5a8-5690-4e35-a6fa-901a88b5774a.jpg";
        byte[] expectedBytes = "binary-stream-data-from-appwrite".getBytes(StandardCharsets.UTF_8);

        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/connect-storage/files/po_8d89e5a856904e35a6fa901a88b5774a/download"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Appwrite-Project", "backend-storage-hub"))
                .andRespond(withSuccess(expectedBytes, MediaType.APPLICATION_OCTET_STREAM));

        try (InputStream is = service.load(objectKey)) {
            assertNotNull(is);
            byte[] actualBytes = is.readAllBytes();
            assertArrayEquals(expectedBytes, actualBytes);
        }

        mockServer.verify();
    }

    @Test
    @DisplayName("Xóa file thành công qua delete()")
    void testDeleteMediaSuccess() {
        String objectKey = "avatar/2026/10/8d89e5a8-5690-4e35-a6fa-901a88b5774a.jpg";

        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/connect-storage/files/av_8d89e5a856904e35a6fa901a88b5774a"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertDoesNotThrow(() -> service.delete(objectKey));
        mockServer.verify();
    }

    @Test
    @DisplayName("Xóa file khi file không tồn tại (404) vẫn coi là thành công (Idempotent Deletion)")
    void testDeleteMediaNotFoundIdempotent() {
        String objectKey = "post/2026/10/8d89e5a8-5690-4e35-a6fa-901a88b5774a.jpg";

        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/connect-storage/files/po_8d89e5a856904e35a6fa901a88b5774a"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"message\":\"File not found\",\"code\":404}"));

        assertDoesNotThrow(() -> service.delete(objectKey));
        mockServer.verify();
    }

    @Test
    @DisplayName("Upload thất bại khi Appwrite trả lỗi 500 ném đúng StorageException")
    void testStoreFailureThrowsStorageException() {
        String objectKey = "post/2026/10/8d89e5a8-5690-4e35-a6fa-901a88b5774a.jpg";
        byte[] fakeImage = "sample".getBytes(StandardCharsets.UTF_8);

        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/connect-storage/files"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("Internal Server Error"));

        assertThrows(StorageException.class, () ->
                service.store(new ByteArrayInputStream(fakeImage), fakeImage.length, "image/jpeg", objectKey)
        );

        mockServer.verify();
    }

    @Test
    @DisplayName("getProviderName trả về đúng định danh APPWRITE")
    void testGetProviderName() {
        assertEquals("APPWRITE", service.getProviderName());
    }

    @Test
    @DisplayName("generateThumbnailUrl trả về đúng Appwrite Preview WebP URL cho Public Media")
    void testGenerateThumbnailUrlPublicMedia() {
        String objectKey = "post/2026/10/8d89e5a8-5690-4e35-a6fa-901a88b5774a.jpg";
        String thumbUrl = service.generateThumbnailUrl(objectKey, 360, 360);

        assertNotNull(thumbUrl);
        assertTrue(thumbUrl.startsWith("https://fra.cloud.appwrite.io/v1/storage/buckets/connect-storage/files/po_8d89e5a856904e35a6fa901a88b5774a/preview"));
        assertTrue(thumbUrl.contains("width=360"));
        assertTrue(thumbUrl.contains("height=360"));
        assertTrue(thumbUrl.contains("output=webp"));
        assertTrue(thumbUrl.contains("project=backend-storage-hub"));
    }

    @Test
    @DisplayName("generateThumbnailUrl trả về null cho CHAT (Private) hoặc null/blank objectKey")
    void testGenerateThumbnailUrlPrivateOrEmpty() {
        String chatKey = "chat/2026/10/8d89e5a8-5690-4e35-a6fa-901a88b5774a.png";
        assertNull(service.generateThumbnailUrl(chatKey, 360, 360));
        assertNull(service.generateThumbnailUrl(null, 360, 360));
        assertNull(service.generateThumbnailUrl("   ", 360, 360));
    }
}
