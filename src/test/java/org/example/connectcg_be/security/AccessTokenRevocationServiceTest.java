package org.example.connectcg_be.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationServiceException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenRevocationServiceTest {
    private StringRedisTemplate redisTemplate;
    private JwtTokenProvider tokenProvider;
    private AccessTokenRevocationService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        tokenProvider = mock(JwtTokenProvider.class);
        service = new AccessTokenRevocationService(redisTemplate, tokenProvider);
        when(tokenProvider.getTokenId("jwt")).thenReturn("jti-1");
        when(tokenProvider.getSessionId("jwt")).thenReturn("family-1");
    }

    @Test
    void rejectsRevokedSession() {
        when(redisTemplate.hasKey("auth:revoked:token:jti-1")).thenReturn(false);
        when(redisTemplate.hasKey("auth:revoked:session:family-1")).thenReturn(true);

        assertTrue(service.isRevoked("jwt"));
    }

    @Test
    void failsClosedWhenRedisResultIsIndeterminate() {
        when(redisTemplate.hasKey("auth:revoked:token:jti-1")).thenReturn(null);
        when(redisTemplate.hasKey("auth:revoked:session:family-1")).thenReturn(false);

        assertThrows(AuthenticationServiceException.class, () -> service.isRevoked("jwt"));
    }
}
