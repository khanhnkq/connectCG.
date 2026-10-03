package org.example.connectcg_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.dto.MediaUploadResponse;
import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.MediaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
    private final org.example.connectcg_be.queue.producer.VideoQueueProducer videoQueueProducer;
    private final VideoOptimizationService videoOptimizationService;

    public MediaUploadResponse upload(MultipartFile file, String categoryValue, Integer uploaderId) {
        return upload(file, categoryValue, uploaderId, null);
    }

    public MediaUploadResponse upload(MultipartFile file, String categoryValue, Integer uploaderId, MultipartFile thumbnail) {
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

        // Lưu thumbnail phía client nếu có gửi kèm (đặc biệt cho video)
        if ("VIDEO".equalsIgnoreCase(validated.mediaType()) && thumbnail != null && !thumbnail.isEmpty()) {
            try {
                ValidatedMedia thumbValidated = mediaFileValidator.validate(thumbnail);
                if ("IMAGE".equalsIgnoreCase(thumbValidated.mediaType())) {
                    String thumbObjectKey = category.path() + "/" + YearMonth.now().format(YEAR_MONTH_PATH) + "/thumb_"
                            + UUID.randomUUID() + "." + thumbValidated.extension();
                    StoredObject storedThumb = objectStorageService.store(
                            thumbnail.getInputStream(), thumbnail.getSize(), thumbValidated.contentType(), thumbObjectKey);
                    media.setThumbnailUrl(storedThumb.url());
                    log.info("Client video thumbnail stored: key={} url={}", thumbObjectKey, storedThumb.url());
                }
            } catch (Exception e) {
                log.warn("Could not save client-provided video thumbnail: {}", e.getMessage());
            }
        }

        try {
            Media saved = mediaRepository.save(media);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("media_upload_success mediaId={} userId={} category={} contentType={} size={} durationMs={}",
                    saved.getId(), uploaderId, category.path(), validated.contentType(), file.getSize(), durationMs);

            // Enqueue async processing
            if ("VIDEO".equalsIgnoreCase(saved.getType())) {
                try {
                    videoQueueProducer.enqueueVideoProcessing(
                            saved.getId(),
                            saved.getObjectKey(),
                            saved.getCategory(),
                            saved.getContentType(),
                            saved.getSizeBytes() != null ? saved.getSizeBytes().longValue() : file.getSize()
                    );
                } catch (Exception e) {
                    log.warn("Failed to enqueue async video processing for media [{}]: {}", saved.getId(), e.getMessage());
                }
            } else if ("IMAGE".equalsIgnoreCase(saved.getType())) {
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
            }

            return new MediaUploadResponse(
                    saved.getId(), saved.getObjectKey(), saved.getUrl(), saved.getContentType(), saved.getSizeBytes(), saved.getThumbnailUrl());
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

    @org.springframework.transaction.annotation.Transactional
    public void processAsyncVideo(Integer mediaId, String objectKey, String category, String contentType, long sizeBytes) {
        Media media = mediaRepository.findById(mediaId).orElse(null);
        if (media == null || Boolean.TRUE.equals(media.getIsDeleted())) {
            log.warn("Video media [{}] not found or deleted, aborting async video processing", mediaId);
            return;
        }

        File rawTempFile = null;
        File compressedTempFile = null;
        File thumbTempFile = null;

        try {
            rawTempFile = File.createTempFile("raw_vid_" + mediaId + "_", ".tmp");
            rawTempFile.deleteOnExit();

            try (InputStream is = objectStorageService.load(objectKey);
                 FileOutputStream fos = new FileOutputStream(rawTempFile)) {
                if (is == null) {
                    log.warn("Could not load object {} for video processing", objectKey);
                    return;
                }
                is.transferTo(fos);
            }

            VideoOptimizationService.VideoMetadata metadata = videoOptimizationService.probeVideoMetadata(rawTempFile);
            long actualSizeBytes = metadata.sizeBytes() > 0 ? metadata.sizeBytes() : rawTempFile.length();

            // 1. Tạo poster thumbnail nếu chưa có (client chưa gửi hoặc gửi lỗi)
            if (media.getThumbnailUrl() == null || media.getThumbnailUrl().isBlank()) {
                thumbTempFile = File.createTempFile("thumb_vid_" + mediaId + "_", ".jpg");
                thumbTempFile.deleteOnExit();
                boolean thumbExtracted = videoOptimizationService.extractThumbnail(rawTempFile, thumbTempFile, metadata.duration());
                if (thumbExtracted && thumbTempFile.exists() && thumbTempFile.length() > 0) {
                    int firstSlash = objectKey.indexOf('/');
                    String thumbObjectKey;
                    if (firstSlash > 0) {
                        thumbObjectKey = objectKey.substring(0, firstSlash) + "/thumbnails/" + objectKey.substring(firstSlash + 1);
                    } else {
                        thumbObjectKey = "thumbnails/" + objectKey;
                    }
                    int lastDot = thumbObjectKey.lastIndexOf('.');
                    if (lastDot > 0) {
                        thumbObjectKey = thumbObjectKey.substring(0, lastDot) + ".jpg";
                    }

                    try (InputStream tis = new FileInputStream(thumbTempFile)) {
                        StoredObject storedThumb = objectStorageService.store(
                                tis, thumbTempFile.length(), "image/jpeg", thumbObjectKey);
                        media.setThumbnailUrl(storedThumb.url());
                        log.info("Backend generated video thumbnail: key={} url={}", thumbObjectKey, storedThumb.url());
                    }
                }
            }

            // 2. Kiểm tra điều kiện nén 720p: dưới 5MB hoặc <= 720p thì bypass
            boolean shouldCompress = videoOptimizationService.shouldCompress(
                    actualSizeBytes,
                    metadata.width(),
                    metadata.height()
            );

            if (shouldCompress) {
                compressedTempFile = File.createTempFile("comp_vid_" + mediaId + "_", ".mp4");
                compressedTempFile.deleteOnExit();
                boolean compressSuccess = videoOptimizationService.compressTo720p(rawTempFile, compressedTempFile);
                if (compressSuccess && compressedTempFile.exists() && compressedTempFile.length() < rawTempFile.length()) {
                    try (InputStream cis = new FileInputStream(compressedTempFile)) {
                        StoredObject storedComp = objectStorageService.store(
                                cis, compressedTempFile.length(), "video/mp4", objectKey);
                        media.setSizeBytes(Math.toIntExact(compressedTempFile.length()));
                        media.setContentType("video/mp4");
                        log.info("Replaced video [{}] with 720p compressed version. Old size: {}KB, New size: {}KB",
                                mediaId, rawTempFile.length() / 1024, compressedTempFile.length() / 1024);
                    }
                } else {
                    log.info("Compression for video [{}] did not reduce size or failed; keeping original.", mediaId);
                }
            }

            mediaRepository.save(media);
            log.info("Completed async video processing for media ID: {}", mediaId);

        } catch (Exception e) {
            log.error("Failed async video processing for media [{}]: {}", mediaId, e.getMessage(), e);
            throw new RuntimeException("Async video processing failed", e);
        } finally {
            if (rawTempFile != null && rawTempFile.exists()) {
                rawTempFile.delete();
            }
            if (compressedTempFile != null && compressedTempFile.exists()) {
                compressedTempFile.delete();
            }
            if (thumbTempFile != null && thumbTempFile.exists()) {
                thumbTempFile.delete();
            }
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
