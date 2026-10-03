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
import java.io.InputStream;
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
    private final ImageOptimizationService imageOptimizationService;
    private final org.example.connectcg_be.queue.producer.MediaQueueProducer mediaQueueProducer;

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

        ImageOptimizationService.OptimizedImage optimized;
        try {
            optimized = imageOptimizationService.optimize(file.getInputStream(), file.getSize(), validated, category);
        } catch (IOException e) {
            throw new MediaValidationException("Không thể đọc file upload", e);
        }

        String objectKey = category.path() + "/" + YearMonth.now().format(YEAR_MONTH_PATH) + "/"
                + UUID.randomUUID() + "." + optimized.extension();
        StoredObject stored = objectStorageService.store(
                optimized.inputStream(), optimized.sizeBytes(), optimized.contentType(), objectKey);

        Media media = new Media();
        media.setUploader(uploader);
        media.setUrl(stored.url());
        media.setType(validated.mediaType());
        media.setSizeBytes(Math.toIntExact(optimized.sizeBytes()));
        media.setStorageProvider("MINIO");
        media.setStorageBucket(stored.bucket());
        media.setObjectKey(stored.objectKey());
        media.setContentType(optimized.contentType());
        media.setCategory(category.path().toUpperCase());
        media.setUploadedAt(Instant.now());
        media.setIsDeleted(false);

        try {
            Media saved = mediaRepository.save(media);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("media_upload_success mediaId={} userId={} category={} contentType={} size={} durationMs={}",
                    saved.getId(), uploaderId, category.path(), validated.contentType(), file.getSize(), durationMs);

            // Enqueue async thumbnail processing
            try {
                mediaQueueProducer.enqueueMediaProcessing(
                        saved.getId(),
                        saved.getObjectKey(),
                        saved.getType(),
                        saved.getCategory(),
                        saved.getContentType()
                );
            } catch (Exception e) {
                log.warn("Failed to enqueue async media processing for media [{}]: {}", saved.getId(), e.getMessage());
            }

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

    @org.springframework.transaction.annotation.Transactional
    public void processAsyncMedia(Integer mediaId, String objectKey, String mediaType, String category) {
        if (!"IMAGE".equalsIgnoreCase(mediaType)) {
            log.info("Media [{}] is not an image (type: {}), skipping thumbnail generation", mediaId, mediaType);
            return;
        }

        Media media = mediaRepository.findById(mediaId).orElse(null);
        if (media == null || Boolean.TRUE.equals(media.getIsDeleted())) {
            log.warn("Media [{}] not found or deleted, aborting async media processing", mediaId);
            return;
        }

        if (media.getThumbnailUrl() != null && !media.getThumbnailUrl().isBlank()) {
            log.info("Media [{}] already has thumbnail: {}", mediaId, media.getThumbnailUrl());
            return;
        }

        try (InputStream is = objectStorageService.load(objectKey)) {
            if (is == null) {
                log.warn("Could not load object {} for thumbnail generation", objectKey);
                return;
            }

            MediaCategory cat = null;
            try {
                cat = MediaCategory.from(category);
            } catch (Exception ignored) {}

            int width = (cat == MediaCategory.AVATAR) ? 150 : 360;
            int height = (cat == MediaCategory.AVATAR) ? 150 : 360;

            ImageOptimizationService.ThumbnailResult thumb = imageOptimizationService.generateThumbnail(is, width, height);
            if (thumb == null || thumb.data() == null || thumb.data().length == 0) {
                log.warn("Thumbnail generation returned empty result for media [{}]", mediaId);
                return;
            }

            // Tạo objectKey cho thumbnail trong cùng category: e.g. avatar/thumbnails/2026/08/uuid.jpg
            int firstSlash = objectKey.indexOf('/');
            String thumbObjectKey;
            if (firstSlash > 0) {
                thumbObjectKey = objectKey.substring(0, firstSlash) + "/thumbnails/" + objectKey.substring(firstSlash + 1);
            } else {
                thumbObjectKey = "thumbnails/" + objectKey;
            }

            int lastDot = thumbObjectKey.lastIndexOf('.');
            if (lastDot > 0) {
                thumbObjectKey = thumbObjectKey.substring(0, lastDot) + "." + thumb.extension();
            }

            StoredObject storedThumb = objectStorageService.store(
                    new java.io.ByteArrayInputStream(thumb.data()),
                    thumb.data().length,
                    thumb.contentType(),
                    thumbObjectKey
            );

            media.setThumbnailUrl(storedThumb.url());
            mediaRepository.save(media);

            log.info("media_thumbnail_generated mediaId={} thumbKey={} thumbSize={} url={}",
                    mediaId, thumbObjectKey, thumb.data().length, storedThumb.url());
        } catch (Exception e) {
            log.error("Failed to process async media thumbnail for media [{}]: {}", mediaId, e.getMessage(), e);
            throw new RuntimeException("Async media processing failed", e);
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
