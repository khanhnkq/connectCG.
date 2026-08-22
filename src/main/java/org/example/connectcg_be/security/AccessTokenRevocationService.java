package org.example.connectcg_be.security;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class AccessTokenRevocationService {
    private static final String TOKEN_PREFIX = "auth:revoked:token:";
    private static final String SESSION_PREFIX = "auth:revoked:session:";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider tokenProvider;

    public AccessTokenRevocationService(StringRedisTemplate redisTemplate, JwtTokenProvider tokenProvider) {
        this.redisTemplate = redisTemplate;
        this.tokenProvider = tokenProvider;
    }

    public boolean isRevoked(String token) {
        try {
            Boolean tokenRevoked = redisTemplate.hasKey(TOKEN_PREFIX + tokenProvider.getTokenId(token));
            Boolean sessionRevoked = redisTemplate.hasKey(SESSION_PREFIX + tokenProvider.getSessionId(token));
            if (tokenRevoked == null || sessionRevoked == null) {
                throw new AuthenticationServiceException("Redis returned an indeterminate revocation result");
            }
            return tokenRevoked || sessionRevoked;
        } catch (DataAccessException exception) {
            throw new AuthenticationServiceException("Token revocation store is unavailable", exception);
        }
    }

    public void revokeToken(String token) {
        if (token == null || !tokenProvider.validateToken(token)) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), tokenProvider.getExpiration(token));
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        set(TOKEN_PREFIX + tokenProvider.getTokenId(token), ttl);
    }

    public void revokeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        set(SESSION_PREFIX + sessionId, tokenProvider.getAccessTokenLifetime());
    }

    private void set(String key, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, "1", ttl);
        } catch (DataAccessException exception) {
            throw new AuthenticationServiceException("Token revocation store is unavailable", exception);
        }
    }
}
