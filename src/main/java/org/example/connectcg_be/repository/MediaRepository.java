package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<Media, Integer> {
    List<Media> findAllByUploaderIdAndIsDeletedFalseOrderByUploadedAtDesc(Integer uploaderId);

    Optional<Media> findByUrlAndUploaderIdAndIsDeletedFalse(String url, Integer uploaderId);

    @Query(value = """
            SELECT m.*
            FROM media m
            LEFT JOIN user_avatars ua ON ua.media_id = m.id
            LEFT JOIN user_covers uc ON uc.media_id = m.id
            LEFT JOIN user_gallery ug ON ug.media_id = m.id
            LEFT JOIN post_media pm ON pm.media_id = m.id
            LEFT JOIN comments c ON c.media_id = m.id
            LEFT JOIN groups g ON g.cover_media_id = m.id
            LEFT JOIN chat_rooms cr ON cr.avatar_url = m.url
            WHERE m.storage_provider = 'MINIO'
              AND m.is_deleted = FALSE
              AND m.uploaded_at < :cutoff
              AND (m.category IS NULL OR m.category <> 'CHAT')
              AND ua.id IS NULL
              AND uc.id IS NULL
              AND ug.id IS NULL
              AND pm.media_id IS NULL
              AND c.id IS NULL
              AND g.id IS NULL
              AND cr.id IS NULL
            ORDER BY m.id
            """, nativeQuery = true)
    List<Media> findUnattachedMinioMedia(@Param("cutoff") Instant cutoff, Pageable pageable);
}
