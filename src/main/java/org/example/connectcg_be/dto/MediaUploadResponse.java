package org.example.connectcg_be.dto;

public record MediaUploadResponse(
        Integer mediaId,
        String objectKey,
        String url,
        String contentType,
        Integer size) {
}
