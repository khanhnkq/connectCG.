package org.example.connectcg_be.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppwriteStorageUtils {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
    );

    private static final Pattern VALID_STANDALONE_ID = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,35}$");

    private AppwriteStorageUtils() {}

    /**
     * Chuyển đổi tất định một hierarchical storage key thành Appwrite fileId hợp lệ.
     * Quy tắc Appwrite: Max 36 ký tự, chỉ chứa [a-zA-Z0-9._-], bắt đầu bằng chữ hoặc số.
     */
    public static String toAppwriteId(String key) {
        if (key == null || key.isBlank()) {
            return "f_" + randomHex(32);
        }

        String cleaned = key.trim();

        // Nếu key vốn dĩ đã là standalone ID hợp lệ không chứa dấu '/'
        if (!cleaned.contains("/") && VALID_STANDALONE_ID.matcher(cleaned).matches()) {
            return cleaned;
        }

        String lowerKey = cleaned.toLowerCase();
        Matcher matcher = UUID_PATTERN.matcher(cleaned);
        String lastUuid = null;
        while (matcher.find()) {
            lastUuid = matcher.group(1);
        }

        if (lastUuid != null) {
            String mediaHex = lastUuid.replace("-", "").toLowerCase();
            if (lowerKey.contains("thumbnail") || lowerKey.contains("thumb_")) {
                return "th_" + mediaHex; // 35 ký tự
            }
            if (lowerKey.contains("avatar")) {
                return "av_" + mediaHex; // 35 ký tự
            }
            if (lowerKey.contains("cover")) {
                return "cv_" + mediaHex; // 35 ký tự
            }
            if (lowerKey.contains("post")) {
                return "po_" + mediaHex; // 35 ký tự
            }
            if (lowerKey.contains("comment")) {
                return "cm_" + mediaHex; // 35 ký tự
            }
            if (lowerKey.contains("group")) {
                return "gr_" + mediaHex; // 35 ký tự
            }
            if (lowerKey.contains("chat")) {
                return "ch_" + mediaHex; // 35 ký tự
            }
            return "rw_" + mediaHex; // 35 ký tự (raw media)
        }

        // Fallback: SHA-256 hash tất định (33 ký tự hex + prefix "f_" = 35 ký tự)
        String hash = sha256Hex(cleaned);
        return "f_" + hash.substring(0, Math.min(33, hash.length()));
    }

    /**
     * Chuẩn hóa tên file gửi lên Appwrite để luôn có extension hợp lệ phù hợp với MIME type.
     */
    public static String toSafeFilename(String objectKey, String contentType) {
        if (objectKey == null || objectKey.isBlank()) {
            return "file.bin";
        }
        int lastSlash = objectKey.lastIndexOf('/');
        String rawName = (lastSlash >= 0) ? objectKey.substring(lastSlash + 1) : objectKey;

        int dotIndex = rawName.lastIndexOf('.');
        String ext = (dotIndex >= 0) ? rawName.substring(dotIndex).toLowerCase() : "";

        if (ext.equals(".mp4") || ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".webp")) {
            return rawName;
        }

        String stem = (dotIndex >= 0) ? rawName.substring(0, dotIndex) : rawName;
        if (contentType != null && contentType.startsWith("video/")) {
            return stem + ".mp4";
        }
        if (contentType != null && (contentType.equals("image/png") || ext.equals(".png"))) {
            return stem + ".png";
        }
        if (contentType != null && (contentType.equals("image/webp") || ext.equals(".webp"))) {
            return stem + ".webp";
        }
        return stem + ".jpg";
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String randomHex(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, Math.min(length, 32));
    }
}
