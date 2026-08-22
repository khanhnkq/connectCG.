package org.example.connectcg_be.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitExceptionHandlerTest {
    @Test
    void returnsTooManyRequestsWithRetryAfterHeader() {
        RateLimitExceptionHandler handler = new RateLimitExceptionHandler();

        ResponseEntity<RateLimitExceptionHandler.RateLimitErrorResponse> response =
                handler.handle(new RateLimitExceededException(42));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("42", response.getHeaders().getFirst("Retry-After"));
        assertEquals(42, response.getBody().retryAfterSeconds());
    }
}
