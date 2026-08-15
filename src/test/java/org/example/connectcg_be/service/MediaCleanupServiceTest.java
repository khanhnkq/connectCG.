package org.example.connectcg_be.service;

import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.repository.MediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaCleanupServiceTest {

    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @InjectMocks
    private MediaCleanupService mediaCleanupService;

    @Test
    void deletesUnattachedObjectAndSoftDeletesMetadata() {
        Media media = new Media();
        media.setObjectKey("post/2026/08/orphan.png");
        media.setIsDeleted(false);
        when(mediaRepository.findUnattachedMinioMedia(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(media));

        int cleaned = mediaCleanupService.cleanupOlderThan(Instant.now(), 100);

        assertTrue(media.getIsDeleted());
        verify(objectStorageService).delete("post/2026/08/orphan.png");
        verify(mediaRepository).save(media);
        assertTrue(cleaned == 1);
    }
}
