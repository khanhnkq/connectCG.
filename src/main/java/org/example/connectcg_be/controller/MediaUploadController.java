package org.example.connectcg_be.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.connectcg_be.dto.MediaUploadResponse;
import org.example.connectcg_be.ratelimit.RateLimitPolicy;
import org.example.connectcg_be.ratelimit.RateLimitService;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.MediaUploadService;
import org.example.connectcg_be.service.ObjectStorageService;
import org.example.connectcg_be.service.StorageException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaUploadController {
    private final MediaUploadService mediaUploadService;
    private final RateLimitService rateLimitService;
    private final ObjectStorageService objectStorageService;

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        rateLimitService.check(RateLimitPolicy.MEDIA_UPLOAD, currentUser.getId().toString());
        MediaUploadResponse response = (thumbnail != null && !thumbnail.isEmpty())
                ? mediaUploadService.upload(file, category, currentUser.getId(), thumbnail)
                : mediaUploadService.upload(file, category, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public ResponseEntity<MediaUploadResponse> upload(
            MultipartFile file,
            String category,
            UserPrincipal currentUser) {
        return upload(file, category, null, currentUser);
    }

    @GetMapping("/view/**")
    public ResponseEntity<Resource> viewMedia(
            HttpServletRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchingPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String objectKey = new AntPathMatcher().extractPathWithinPattern(bestMatchingPattern, fullPath);

        if (objectKey.startsWith("connect-media/")) {
            objectKey = objectKey.substring("connect-media/".length());
        }

        org.example.connectcg_be.service.MediaCategory category =
                org.example.connectcg_be.service.MediaCategory.fromObjectKey(objectKey);
        boolean isPublic = (category != null && category.isPublic());

        if (!isPublic && currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            InputStream is = objectStorageService.load(objectKey);
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            String lower = objectKey.toLowerCase();
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                mediaType = MediaType.IMAGE_JPEG;
            } else if (lower.endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            } else if (lower.endsWith(".gif")) {
                mediaType = MediaType.IMAGE_GIF;
            } else if (lower.endsWith(".webp")) {
                mediaType = MediaType.parseMediaType("image/webp");
            } else if (lower.endsWith(".mp4")) {
                mediaType = MediaType.parseMediaType("video/mp4");
            } else if (lower.endsWith(".webm")) {
                mediaType = MediaType.parseMediaType("video/webm");
            }

            CacheControl cacheControl = isPublic
                    ? CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable()
                    : CacheControl.noCache().cachePrivate();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(cacheControl)
                    .body(new InputStreamResource(is));
        } catch (StorageException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
