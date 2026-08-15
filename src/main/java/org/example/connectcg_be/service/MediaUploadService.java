package org.example.connectcg_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.dto.MediaUploadResponse;
import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.MediaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaUploadService {
    private static final DateTimeFormatter YEAR_MONTH_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final ObjectStorageService objectStorageService;
    private final MediaRepository mediaRepository;
    private final UserService userService;
    private final MediaFileValidator mediaFileValidator;

    public MediaUploadResponse upload(MultipartFile file, String categoryValue, Integer uploaderId) {
        long startedAt = System.nanoTime();
        MediaCategory category = MediaCategory.from(categoryValue);
        ValidatedMedia validated = mediaFileValidator.validate(file);
        if ("VIDEO".equals(validated.mediaType()) && !category.videoAllowed()) {
            throw new MediaValidationException("Category này không hỗ trợ video");
        }

        User uploader = userService.findByIdUser(uploaderId);
        if (uploader == null) {
            throw new MediaValidationException("Không tìm thấy người upload");
        }

        String objectKey = category.path() + "/" + YearMonth.now().format(YEAR_MONTH_PATH) + "/"
                + UUID.randomUUID() + "." + validated.extension();
        StoredObject stored = store(file, validated, objectKey);

        Media media = new Media();
        media.setUploader(uploader);
        media.setUrl(stored.url());
        media.setType(validated.mediaType());
        media.setSizeBytes(Math.toIntExact(file.getSize()));
        media.setStorageProvider("MINIO");
        media.setStorageBucket(stored.bucket());
        media.setObjectKey(stored.objectKey());
        media.setContentType(validated.contentType());
        media.setCategory(category.path().toUpperCase());
        media.setUploadedAt(Instant.now());
        media.setIsDeleted(false);

        try {
            Media saved = mediaRepository.save(media);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("media_upload_success mediaId={} userId={} category={} contentType={} size={} durationMs={}",
                    saved.getId(), uploaderId, category.path(), validated.contentType(), file.getSize(), durationMs);
            return new MediaUploadResponse(
                    saved.getId(), saved.getObjectKey(), saved.getUrl(), saved.getContentType(), saved.getSizeBytes());
        } catch (RuntimeException exception) {
            try {
                objectStorageService.delete(stored.objectKey());
            } catch (RuntimeException cleanupError) {
                exception.addSuppressed(cleanupError);
            }
            throw exception;
        }
    }

    private StoredObject store(MultipartFile file, ValidatedMedia validated, String objectKey) {
        try {
            return objectStorageService.store(
                    file.getInputStream(), file.getSize(), validated.contentType(), objectKey);
        } catch (IOException exception) {
            throw new MediaValidationException("Không thể đọc file upload", exception);
        }
    }
}
