package org.example.connectcg_be.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.example.connectcg_be.entity.RefreshToken;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.RefreshTokenRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.security.RefreshTokenReuseException;
import org.example.connectcg_be.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceImplTest {
    private RefreshTokenRepository refreshTokenRepository;
    private UserRepository userRepository;
    private HttpServletRequest request;
    private RefreshTokenServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        userRepository = mock(UserRepository.class);
        request = mock(HttpServletRequest.class);
        service = new RefreshTokenServiceImpl(
                refreshTokenRepository,
                userRepository,
                Duration.ofDays(30));

        user = new User();
        user.setId(7);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hash");
        user.setRole("USER");
        user.setIsEnabled(true);
        user.setIsDeleted(false);
        user.setIsLocked(false);
        user.setPermanentLocked(false);
        user.setAuthVersion(0);

        when(request.getHeader("User-Agent")).thenReturn("test-browser");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issueStoresOnlyHashAndSessionMetadata() {
        RefreshTokenService.IssuedRefreshToken issued = service.issue(user, request);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken stored = captor.getValue();

        assertNotEquals(issued.rawToken(), stored.getTokenHash());
        assertEquals(64, stored.getTokenHash().length());
        assertEquals(issued.familyId(), stored.getFamilyId());
        assertEquals("test-browser", stored.getUserAgent());
        assertEquals("127.0.0.1", stored.getIpAddress());
        assertTrue(stored.getExpiresAt().isAfter(Instant.now().plus(Duration.ofDays(29))));
        assertFalse(stored.getIsRevoked());
    }

    @Test
    void rotateInvalidatesPreviousTokenAndKeepsFamily() {
        RefreshTokenService.IssuedRefreshToken issued = service.issue(user, request);
        ArgumentCaptor<RefreshToken> initialCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(initialCaptor.capture());
        RefreshToken current = initialCaptor.getValue();
        when(refreshTokenRepository.findByTokenHashForUpdate(current.getTokenHash()))
                .thenReturn(Optional.of(current));

        RefreshTokenService.IssuedRefreshToken rotated = service.rotate(issued.rawToken(), request);

        assertNotEquals(issued.rawToken(), rotated.rawToken());
        assertEquals(issued.familyId(), rotated.familyId());
        assertTrue(current.getIsRevoked());
        assertEquals("ROTATED", current.getRevokedReason());
        assertNotNull(current.getReplacedByHash());
    }

    @Test
    void reusedRotatedTokenRevokesActiveFamily() {
        RefreshToken reused = token("family-1", true, "ROTATED");
        RefreshToken active = token("family-1", false, null);
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(reused));
        when(refreshTokenRepository.findAllByFamilyIdAndIsRevokedFalse("family-1"))
                .thenReturn(List.of(active));

        RefreshTokenReuseException exception = assertThrows(
                RefreshTokenReuseException.class,
                () -> service.rotate("stolen-old-token", request));

        assertEquals("family-1", exception.getFamilyId());
        assertTrue(active.getIsRevoked());
        assertEquals("REUSE_DETECTED", active.getRevokedReason());
    }

    @Test
    void logoutAllIncrementsDurableAuthVersionAndRevokesRefreshTokens() {
        RefreshToken active = token("family-1", false, null);
        when(userRepository.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findAllByUserIdAndIsRevokedFalse(7))
                .thenReturn(List.of(active));

        service.revokeAllForUser(7, "LOGOUT_ALL", true);

        assertEquals(1, user.getAuthVersion());
        assertTrue(active.getIsRevoked());
        assertEquals("LOGOUT_ALL", active.getRevokedReason());
    }

    private RefreshToken token(String familyId, boolean revoked, String reason) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash("a".repeat(64));
        token.setFamilyId(familyId);
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        token.setIsRevoked(revoked);
        token.setRevokedReason(reason);
        return token;
    }
}
