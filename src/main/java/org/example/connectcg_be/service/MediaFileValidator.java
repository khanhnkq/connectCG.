package org.example.connectcg_be.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Component
public class MediaFileValidator {
    private static final long IMAGE_LIMIT = 5L * 1024 * 1024;
    private static final long VIDEO_LIMIT = 50L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "video/mp4", "mp4",
            "video/webm", "webm");

    public ValidatedMedia validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MediaValidationException("File là bắt buộc");
        }

        String declaredType = normalize(file.getContentType());
        if (!EXTENSIONS.containsKey(declaredType)) {
            throw new MediaValidationException("Định dạng file không được hỗ trợ");
        }

        boolean video = declaredType.startsWith("video/");
        long limit = video ? VIDEO_LIMIT : IMAGE_LIMIT;
        if (file.getSize() > limit) {
            throw new MediaValidationException(video
                    ? "Video vượt quá giới hạn 50 MB"
                    : "Ảnh vượt quá giới hạn 5 MB");
        }

        String detectedType = detectContentType(file);
        if (!declaredType.equals(detectedType)) {
            throw new MediaValidationException("Nội dung file không khớp định dạng được khai báo");
        }

        return new ValidatedMedia(declaredType, EXTENSIONS.get(declaredType), video ? "VIDEO" : "IMAGE");
    }

    private String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private String detectContentType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(16);
            if (startsWith(header, new int[] {0xff, 0xd8, 0xff})) return "image/jpeg";
            if (startsWith(header, new int[] {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) return "image/png";
            if (asciiAt(header, 0, "GIF87a") || asciiAt(header, 0, "GIF89a")) return "image/gif";
            if (asciiAt(header, 0, "RIFF") && asciiAt(header, 8, "WEBP")) return "image/webp";
            if (asciiAt(header, 4, "ftyp")) return "video/mp4";
            if (startsWith(header, new int[] {0x1a, 0x45, 0xdf, 0xa3})) return "video/webm";
            return "application/octet-stream";
        } catch (IOException exception) {
            throw new MediaValidationException("Không thể đọc file upload", exception);
        }
    }

    private boolean startsWith(byte[] value, int[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((value[i] & 0xff) != prefix[i]) return false;
        }
        return true;
    }

    private boolean asciiAt(byte[] value, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (value.length < offset + bytes.length) return false;
        for (int i = 0; i < bytes.length; i++) {
            if (value[offset + i] != bytes[i]) return false;
        }
        return true;
    }
}
