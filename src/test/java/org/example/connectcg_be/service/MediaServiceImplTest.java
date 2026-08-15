package org.example.connectcg_be.service;

import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.repository.MediaRepository;
import org.example.connectcg_be.service.impl.MediaServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    @Mock
    private MediaRepository mediaRepository;
    @InjectMocks
    private MediaServiceImpl mediaService;

    @Test
    void resolvesOnlyMediaPreviouslyUploadedByTheSameUser() {
        Media media = new Media();
        when(mediaRepository.findByUrlAndUploaderIdAndIsDeletedFalse("http://minio/media.png", 42))
                .thenReturn(Optional.of(media));

        Media result = mediaService.resolveOwnedMedia("http://minio/media.png", 42);

        assertSame(media, result);
    }

    @Test
    void rejectsUnknownOrForeignMediaUrl() {
        when(mediaRepository.findByUrlAndUploaderIdAndIsDeletedFalse("https://attacker.invalid/file.png", 42))
                .thenReturn(Optional.empty());

        assertThrows(
                MediaValidationException.class,
                () -> mediaService.resolveOwnedMedia("https://attacker.invalid/file.png", 42));
    }
}
