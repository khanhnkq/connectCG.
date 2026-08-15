package org.example.connectcg_be.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaFileValidatorTest {

    private final MediaFileValidator validator = new MediaFileValidator();

    @Test
    void acceptsPngWhenDeclaredTypeMatchesFileSignature() {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
        };
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", png);

        ValidatedMedia validated = validator.validate(file);

        assertEquals("image/png", validated.contentType());
    }

    @Test
    void rejectsSpoofedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "not-a-real-png".getBytes());

        MediaValidationException error = assertThrows(
                MediaValidationException.class,
                () -> validator.validate(file));

        assertEquals("Nội dung file không khớp định dạng được khai báo", error.getMessage());
    }

    @Test
    void rejectsImageLargerThanFiveMegabytes() throws Exception {
        org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito.mock(
                org.springframework.web.multipart.MultipartFile.class);
        org.mockito.Mockito.when(file.isEmpty()).thenReturn(false);
        org.mockito.Mockito.when(file.getSize()).thenReturn(5L * 1024 * 1024 + 1);
        org.mockito.Mockito.when(file.getContentType()).thenReturn("image/png");

        MediaValidationException error = assertThrows(
                MediaValidationException.class,
                () -> validator.validate(file));

        assertEquals("Ảnh vượt quá giới hạn 5 MB", error.getMessage());
    }
}
