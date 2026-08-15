package org.example.connectcg_be.service.impl;

import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.repository.MediaRepository;
import org.example.connectcg_be.service.MediaValidationException;
import org.example.connectcg_be.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;

    @Override
    public Media createCoverMedia(String url, int userId) {
        return resolveOwnedMedia(url, userId);
    }

    @Override
    public Media resolveOwnedMedia(String url, int userId) {
        if (url == null || url.isBlank()) {
            throw new MediaValidationException("Media URL là bắt buộc");
        }
        return mediaRepository.findByUrlAndUploaderIdAndIsDeletedFalse(url, userId)
                .orElseThrow(() -> new MediaValidationException(
                        "Media không tồn tại hoặc không thuộc người dùng hiện tại"));
    }
}
