package org.example.connectcg_be.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class ImageOptimizationServiceTest {

    private final ImageOptimizationService service = new ImageOptimizationService();

    @Test
    void normalImage_PassesValidationAndOptimizes() throws IOException {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] bytes = baos.toByteArray();

        assertDoesNotThrow(() -> service.validateImageDimensions(bytes));

        ValidatedMedia validated = new ValidatedMedia("image/png", "png", "IMAGE");
        ImageOptimizationService.OptimizedImage result = service.optimize(
                new ByteArrayInputStream(bytes),
                bytes.length,
                validated,
                MediaCategory.AVATAR
        );

        assertNotNull(result);
        assertTrue(result.sizeBytes() > 0);
    }

    @Test
    void imageWithExcessiveWidth_ThrowsMediaValidationException() {
        byte[] pngBytes = createSyntheticPng(15000, 100);

        MediaValidationException ex = assertThrows(
                MediaValidationException.class,
                () -> service.validateImageDimensions(pngBytes)
        );

        assertTrue(ex.getMessage().contains("Kích thước ảnh quá lớn"));
    }

    @Test
    void imageWithExcessiveTotalPixels_ThrowsMediaValidationException() {
        // 6000 x 5000 = 30,000,000 pixels > 25,000,000
        byte[] pngBytes = createSyntheticPng(6000, 5000);

        MediaValidationException ex = assertThrows(
                MediaValidationException.class,
                () -> service.validateImageDimensions(pngBytes)
        );

        assertTrue(ex.getMessage().contains("Kích thước ảnh quá lớn"));
    }

    @Test
    void optimize_WhenImageExceedsDimensions_PropagatesValidationException() {
        byte[] pngBytes = createSyntheticPng(12000, 100);
        ValidatedMedia validated = new ValidatedMedia("image/png", "png", "IMAGE");

        assertThrows(
                MediaValidationException.class,
                () -> service.optimize(
                        new ByteArrayInputStream(pngBytes),
                        pngBytes.length,
                        validated,
                        MediaCategory.POST
                )
        );
    }

    @Test
    void generateThumbnail_GeneratesResizedImageSuccessfully() throws IOException {
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        byte[] bytes = baos.toByteArray();

        ImageOptimizationService.ThumbnailResult thumb = service.generateThumbnail(
                new ByteArrayInputStream(bytes), 200, 200);

        assertNotNull(thumb);
        assertNotNull(thumb.data());
        assertTrue(thumb.data().length > 0);
        assertEquals("image/jpeg", thumb.contentType());
        assertEquals("jpg", thumb.extension());
    }

    /**
     * Creates a synthetic PNG header with specified dimensions to test header parsing
     * without creating a gigabyte bitmap in memory.
     */
    private byte[] createSyntheticPng(int width, int height) {
        ByteBuffer buffer = ByteBuffer.allocate(33);
        // PNG signature
        buffer.put(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        // IHDR chunk length (13 bytes)
        buffer.putInt(13);

        // IHDR data buffer for CRC calculation
        ByteBuffer ihdrData = ByteBuffer.allocate(17);
        ihdrData.put(new byte[]{'I', 'H', 'D', 'R'});
        ihdrData.putInt(width);
        ihdrData.putInt(height);
        ihdrData.put((byte) 8); // bit depth
        ihdrData.put((byte) 2); // color type (RGB)
        ihdrData.put((byte) 0); // compression
        ihdrData.put((byte) 0); // filter
        ihdrData.put((byte) 0); // interlace

        buffer.put(ihdrData.array());

        CRC32 crc = new CRC32();
        crc.update(ihdrData.array());
        buffer.putInt((int) crc.getValue());

        return buffer.array();
    }
}
