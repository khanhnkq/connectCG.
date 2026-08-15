package org.example.connectcg_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.repository.MediaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCleanupService {
    private final MediaRepository mediaRepository;
    private final ObjectStorageService objectStorageService;

    @Value("${app.media.cleanup.ttl-hours:24}")
    private long ttlHours;

    @Value("${app.media.cleanup.batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${app.media.cleanup.cron:0 30 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void scheduledCleanup() {
        Instant cutoff = Instant.now().minus(ttlHours, ChronoUnit.HOURS);
        int cleaned = cleanupOlderThan(cutoff, batchSize);
        if (cleaned > 0) {
            log.info("media_cleanup_completed cleaned={} cutoff={}", cleaned, cutoff);
        }
    }

    @Transactional
    public int cleanupOlderThan(Instant cutoff, int limit) {
        List<Media> candidates = mediaRepository.findUnattachedMinioMedia(
                cutoff, PageRequest.of(0, Math.max(1, limit)));
        int cleaned = 0;
        for (Media media : candidates) {
            try {
                objectStorageService.delete(media.getObjectKey());
                media.setIsDeleted(true);
                mediaRepository.save(media);
                cleaned++;
            } catch (RuntimeException exception) {
                log.warn("media_cleanup_failed mediaId={} objectKey={}",
                        media.getId(), media.getObjectKey(), exception);
            }
        }
        return cleaned;
    }
}
