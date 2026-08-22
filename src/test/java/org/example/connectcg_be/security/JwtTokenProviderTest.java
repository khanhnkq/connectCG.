package org.example.connectcg_be.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(
                tokenProvider,
                "jwtSecret",
                "746573742d6f6e6c792d6a77742d7365637265742d6d7573742d62652d6c6f6e67");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationInMs", 900_000L);
    }

    @Test
    void accessTokenCarriesRevocationAndVersionClaims() {
        UserPrincipal principal = new UserPrincipal(
                9,
                "alice",
                "alice@example.com",
                "hash",
                true,
                false,
                false,
                List.of(),
                3);

        String token = tokenProvider.generateToken(principal, "session-family");

        assertTrue(tokenProvider.validateToken(token));
        assertEquals(9, tokenProvider.getUserIdFromJWT(token));
        assertEquals("session-family", tokenProvider.getSessionId(token));
        assertEquals(3, tokenProvider.getAuthVersion(token));
        assertNotNull(tokenProvider.getTokenId(token));
    }

    @Test
    void malformedTokenIsRejected() {
        assertFalse(tokenProvider.validateToken("not-a-jwt"));
    }
}
