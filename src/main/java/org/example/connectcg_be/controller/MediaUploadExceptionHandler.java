package org.example.connectcg_be.controller;

import org.example.connectcg_be.service.MediaValidationException;
import org.example.connectcg_be.service.StorageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = MediaUploadController.class)
public class MediaUploadExceptionHandler {

    @ExceptionHandler(MediaValidationException.class)
    public ResponseEntity<Map<String, String>> validation(MediaValidationException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "File vượt quá giới hạn upload"));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, String>> storageUnavailable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "Object storage tạm thời không khả dụng"));
    }
}
