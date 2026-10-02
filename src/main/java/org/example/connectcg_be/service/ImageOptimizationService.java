package org.example.connectcg_be.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ImageOptimizationService {

    public static final int MAX_DIMENSION = 10_000;
    public static final long MAX_PIXELS = 25_000_000L;

    private final Semaphore compressionSemaphore = new Semaphore(Math.max(2, Runtime.getRuntime().availableProcessors()));

    public record OptimizedImage(
            InputStream inputStream,
            long sizeBytes,
            String contentType,
            String extension
    ) {}

    public record ImageSpec(int maxWidth, int maxHeight, float quality) {}

    public ImageSpec getSpecForCategory(MediaCategory category) {
        return switch (category) {
            case AVATAR -> new ImageSpec(500, 500, 0.88f);
            case COVER -> new ImageSpec(1440, 540, 0.85f);
            case POST -> new ImageSpec(1920, 1920, 0.88f);
            case COMMENT, CHAT -> new ImageSpec(960, 960, 0.84f);
            case GROUP -> new ImageSpec(1200, 600, 0.85f);
        };
    }

    public void validateImageDimensions(byte[] imageBytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (iis == null) {
                return;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis, true, false);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS) {
                        throw new MediaValidationException(
                                String.format("Kích thước ảnh quá lớn (%dx%d). Giới hạn tối đa là %dpx mỗi chiều hoặc %d pixels.",
                                        width, height, MAX_DIMENSION, MAX_PIXELS));
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not read image header metadata: {}", e.getMessage());
        }
    }

    /**
     * Tối ưu hóa và nén ảnh theo category trên Backend.
     * Tự động scale tỉ lệ chuẩn và nén chất lượng cao (>80%).
     * Bỏ qua video và ảnh GIF (để giữ nguyên animation).
     */
    public OptimizedImage optimize(InputStream originalInputStream, long originalSize, ValidatedMedia validated, MediaCategory category) {
        if ("VIDEO".equalsIgnoreCase(validated.mediaType()) || "image/gif".equalsIgnoreCase(validated.contentType())) {
            return new OptimizedImage(originalInputStream, originalSize, validated.contentType(), validated.extension());
        }

        ImageSpec spec = getSpecForCategory(category);
        byte[] originalBytes = null;
        try {
            originalBytes = originalInputStream.readAllBytes();
            validateImageDimensions(originalBytes);

            boolean acquired = false;
            try {
                acquired = compressionSemaphore.tryAcquire(5, TimeUnit.SECONDS);
                if (!acquired) {
                    throw new MediaValidationException("Hệ thống xử lý ảnh đang bận, vui lòng thử lại sau giây lát");
                }

                BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
                if (originalImage == null) {
                    log.warn("ImageIO could not decode image format: {}, skipping compression", validated.contentType());
                    return new OptimizedImage(new ByteArrayInputStream(originalBytes), originalSize, validated.contentType(), validated.extension());
                }

                int origWidth = originalImage.getWidth();
                int origHeight = originalImage.getHeight();

                // Nếu ảnh đã nhỏ hơn max bounds và dung lượng nhẹ (< 50KB) thì giữ nguyên
                if (origWidth <= spec.maxWidth() && origHeight <= spec.maxHeight() && originalSize < 50 * 1024) {
                    return new OptimizedImage(new ByteArrayInputStream(originalBytes), originalSize, validated.contentType(), validated.extension());
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                boolean hasAlpha = originalImage.getColorModel() != null && originalImage.getColorModel().hasAlpha();

                String targetFormat = hasAlpha ? "png" : "jpg";
                String targetContentType = hasAlpha ? "image/png" : "image/jpeg";
                String targetExtension = hasAlpha ? "png" : "jpg";

                var builder = Thumbnails.of(originalImage)
                        .size(spec.maxWidth(), spec.maxHeight())
                        .outputFormat(targetFormat);

                if (!hasAlpha) {
                    builder.outputQuality(spec.quality());
                }

                builder.toOutputStream(outputStream);
                byte[] optimizedBytes = outputStream.toByteArray();

                // Nếu dung lượng sau nén lớn hơn hoặc bằng ảnh gốc thì giữ ảnh gốc
                if (optimizedBytes.length >= originalBytes.length) {
                    return new OptimizedImage(new ByteArrayInputStream(originalBytes), originalSize, validated.contentType(), validated.extension());
                }

                log.info("image_optimized category={} originalSize={} optimizedSize={} savedRatio={}%",
                        category.path(),
                        originalSize,
                        optimizedBytes.length,
                        Math.round((1.0 - (double) optimizedBytes.length / originalSize) * 100));

                return new OptimizedImage(
                        new ByteArrayInputStream(optimizedBytes),
                        optimizedBytes.length,
                        targetContentType,
                        targetExtension
                );
            } finally {
                if (acquired) {
                    compressionSemaphore.release();
                }
            }
        } catch (MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Image optimization failed for category: {}, falling back to original: {}", category.path(), e.getMessage());
            InputStream fallbackStream = (originalBytes != null)
                    ? new ByteArrayInputStream(originalBytes)
                    : originalInputStream;
            return new OptimizedImage(fallbackStream, originalSize, validated.contentType(), validated.extension());
        }
    }
}
