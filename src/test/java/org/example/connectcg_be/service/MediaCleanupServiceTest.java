package org.example.connectcg_be.service;

import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.repository.MediaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaCleanupServiceTest {

    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @InjectMocks
    private MediaCleanupService mediaCleanupService;

    @Test
    @DisplayName("Dọn dẹp file mồ côi thành công cho Appwrite provider và đánh dấu isDeleted = true")
    void deletesUnattachedAppwriteObjectAndSoftDeletesMetadata() {
        Media media = new Media();
        media.setId(101);
        media.setObjectKey("post/2026/10/orphan.png");
        media.setIsDeleted(false);

        when(objectStorageService.getProviderName()).thenReturn("APPWRITE");
        when(mediaRepository.findUnattachedMedia(eq("APPWRITE"), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(media));

        int cleaned = mediaCleanupService.cleanupOlderThan(Instant.now(), 100);

        assertTrue(media.getIsDeleted());
        verify(objectStorageService).delete("post/2026/10/orphan.png");
        verify(mediaRepository).save(media);
        assertEquals(1, cleaned);
    }

    @Test
    @DisplayName("Dọn dẹp file mồ côi thành công cho MinIO provider")
    void deletesUnattachedMinioObjectAndSoftDeletesMetadata() {
        Media media = new Media();
        media.setId(102);
        media.setObjectKey("avatar/2026/08/old.png");
        media.setIsDeleted(false);

        when(objectStorageService.getProviderName()).thenReturn("MINIO");
        when(mediaRepository.findUnattachedMedia(eq("MINIO"), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(media));

        int cleaned = mediaCleanupService.cleanupOlderThan(Instant.now(), 100);

        assertTrue(media.getIsDeleted());
        verify(objectStorageService).delete("avatar/2026/08/old.png");
        verify(mediaRepository).save(media);
        assertEquals(1, cleaned);
    }

    @Test
    @DisplayName("Khi xóa Cloud thất bại với RuntimeException, không đánh dấu isDeleted và vẫn tiếp tục vòng lặp")
    void continuesWhenDeleteThrowsException() {
        Media failedMedia = new Media();
        failedMedia.setId(201);
        failedMedia.setObjectKey("post/2026/10/corrupt.png");
        failedMedia.setIsDeleted(false);

        Media successMedia = new Media();
        successMedia.setId(202);
        successMedia.setObjectKey("post/2026/10/valid.png");
        successMedia.setIsDeleted(false);

        when(objectStorageService.getProviderName()).thenReturn("APPWRITE");
        when(mediaRepository.findUnattachedMedia(eq("APPWRITE"), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(failedMedia, successMedia));

        doThrow(new RuntimeException("Cloud delete error")).when(objectStorageService).delete("post/2026/10/corrupt.png");

        int cleaned = mediaCleanupService.cleanupOlderThan(Instant.now(), 100);

        assertEquals(1, cleaned);
        assertTrue(!failedMedia.getIsDeleted());
        assertTrue(successMedia.getIsDeleted());
        verify(mediaRepository, never()).save(failedMedia);
        verify(mediaRepository).save(successMedia);
    }
}
