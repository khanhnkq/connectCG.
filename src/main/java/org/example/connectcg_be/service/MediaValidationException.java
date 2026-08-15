package org.example.connectcg_be.service;

public class MediaValidationException extends RuntimeException {
    public MediaValidationException(String message) {
        super(message);
    }

    public MediaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
