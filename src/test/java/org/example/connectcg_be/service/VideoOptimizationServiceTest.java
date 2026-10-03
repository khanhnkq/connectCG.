package org.example.connectcg_be.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoOptimizationServiceTest {

    private VideoOptimizationService service;

    @BeforeEach
    void setUp() {
        service = new VideoOptimizationService();
    }

    @Test
    @DisplayName("Bypass khi video dung lượng dưới 5MB kể cả độ phân giải 1080p")
    void shouldBypassWhenSizeUnder5MB() {
        long fourMB = 4L * 1024 * 1024;
        assertFalse(service.shouldCompress(fourMB, 1920, 1080), "4MB 1080p phải bypass");
        assertFalse(service.shouldCompress(1024 * 1024, 3840, 2160), "1MB 4K phải bypass");
    }

    @Test
    @DisplayName("Bypass khi video đã ở chuẩn 720p hoặc thấp hơn (ngang)")
    void shouldBypassWhenLandscape720pOrLower() {
        long tenMB = 10L * 1024 * 1024;
        assertFalse(service.shouldCompress(tenMB, 1280, 720), "10MB 1280x720 (720p) phải bypass");
        assertFalse(service.shouldCompress(tenMB, 854, 480), "10MB 480p phải bypass");
        assertFalse(service.shouldCompress(tenMB, 640, 360), "10MB 360p phải bypass");
    }

    @Test
    @DisplayName("Bypass khi video dọc (portrait) đã ở chuẩn 720p (720x1280)")
    void shouldBypassWhenPortrait720pOrLower() {
        long tenMB = 10L * 1024 * 1024;
        assertFalse(service.shouldCompress(tenMB, 720, 1280), "10MB 720x1280 (portrait 720p) phải bypass");
        assertFalse(service.shouldCompress(tenMB, 480, 854), "10MB portrait 480p phải bypass");
    }

    @Test
    @DisplayName("Kích hoạt nén khi dung lượng >= 5MB và độ phân giải > 720p")
    void shouldCompressWhenAbove5MBAndAbove720p() {
        long sixMB = 6L * 1024 * 1024;
        long twentyMB = 20L * 1024 * 1024;

        assertTrue(service.shouldCompress(sixMB, 1920, 1080), "6MB 1080p phải nén");
        assertTrue(service.shouldCompress(twentyMB, 2560, 1440), "20MB 2K phải nén");
        assertTrue(service.shouldCompress(twentyMB, 1080, 1920), "20MB portrait 1080x1920 phải nén");
    }

    @Test
    @DisplayName("Bypass an toàn khi kích thước video không hợp lệ")
    void shouldBypassWhenInvalidDimensions() {
        long tenMB = 10L * 1024 * 1024;
        assertFalse(service.shouldCompress(tenMB, 0, 0));
        assertFalse(service.shouldCompress(tenMB, -1, 1080));
    }
}
