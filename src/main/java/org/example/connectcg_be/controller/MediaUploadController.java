package org.example.connectcg_be.controller;

import lombok.RequiredArgsConstructor;
import org.example.connectcg_be.dto.MediaUploadResponse;
import org.example.connectcg_be.ratelimit.RateLimitPolicy;
import org.example.connectcg_be.ratelimit.RateLimitService;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.MediaUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaUploadController {
    private final MediaUploadService mediaUploadService;
    private final RateLimitService rateLimitService;

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        rateLimitService.check(RateLimitPolicy.MEDIA_UPLOAD, currentUser.getId().toString());
        MediaUploadResponse response = mediaUploadService.upload(file, category, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
