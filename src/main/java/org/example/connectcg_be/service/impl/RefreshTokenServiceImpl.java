package org.example.connectcg_be.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.example.connectcg_be.entity.RefreshToken;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.RefreshTokenRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.security.InvalidRefreshTokenException;
import org.example.connectcg_be.security.RefreshTokenReuseException;
import org.example.connectcg_be.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ROTATED = "ROTATED";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Duration refreshTokenLifetime;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            @Value("${app.auth.refresh-token-lifetime:30d}") Duration refreshTokenLifetime) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenLifetime = refreshTokenLifetime;
    }

    @Override
    @Transactional
    public IssuedRefreshToken issue(User user, HttpServletRequest request) {
        return create(user, UUID.randomUUID().toString(), request);
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedRefreshToken rotate(String rawToken, HttpServletRequest request) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is missing");
        }

        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));
        Instant now = Instant.now();

        if (Boolean.TRUE.equals(current.getIsRevoked())) {
            if (ROTATED.equals(current.getRevokedReason())) {
                revokeFamily(current.getFamilyId(), "REUSE_DETECTED", now);
                throw new RefreshTokenReuseException(current.getFamilyId());
            }
            throw new InvalidRefreshTokenException("Refresh token is revoked");
        }

        if (!current.getExpiresAt().isAfter(now)) {
            revoke(current, "EXPIRED", now);
            throw new InvalidRefreshTokenException("Refresh token is expired");
        }

        User user = current.getUser();
        if (!isUserActive(user)) {
            revokeFamily(current.getFamilyId(), "ACCOUNT_DISABLED", now);
            throw new InvalidRefreshTokenException("User account is not active");
        }

        String nextRawToken = generateRawToken();
        current.setReplacedByHash(hash(nextRawToken));
        revoke(current, ROTATED, now);

        RefreshToken next = buildToken(user, current.getFamilyId(), nextRawToken, request, now);
        refreshTokenRepository.save(next);
        return new IssuedRefreshToken(nextRawToken, current.getFamilyId(), user);
    }

    @Override
    @Transactional
    public Optional<String> revokeCurrent(String rawToken, String reason) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Optional<RefreshToken> token = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken));
        if (token.isEmpty()) {
            return Optional.empty();
        }

        String familyId = token.get().getFamilyId();
        revokeFamily(familyId, reason, Instant.now());
        return Optional.of(familyId);
    }

    @Override
    @Transactional
    public void revokeAllForUser(Integer userId, String reason, boolean incrementAuthVersion) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException("User not found"));

        if (incrementAuthVersion) {
            user.setAuthVersion(currentAuthVersion(user) + 1);
            userRepository.save(user);
        }

        Instant now = Instant.now();
        refreshTokenRepository.findAllByUserIdAndIsRevokedFalse(userId)
                .forEach(token -> revoke(token, reason, now));
    }

    private IssuedRefreshToken create(User user, String familyId, HttpServletRequest request) {
        String rawToken = generateRawToken();
        refreshTokenRepository.save(buildToken(user, familyId, rawToken, request, Instant.now()));
        return new IssuedRefreshToken(rawToken, familyId, user);
    }

    private RefreshToken buildToken(
            User user,
            String familyId,
            String rawToken,
            HttpServletRequest request,
            Instant now) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setFamilyId(familyId);
        token.setUserAgent(request.getHeader("User-Agent"));
        token.setIpAddress(normalizeIp(request.getRemoteAddr()));
        token.setCreatedAt(now);
        token.setLastUsedAt(now);
        token.setExpiresAt(now.plus(refreshTokenLifetime));
        token.setIsRevoked(false);
        return token;
    }

    private void revokeFamily(String familyId, String reason, Instant now) {
        List<RefreshToken> tokens = refreshTokenRepository.findAllByFamilyIdAndIsRevokedFalse(familyId);
        tokens.forEach(token -> revoke(token, reason, now));
    }

    private void revoke(RefreshToken token, String reason, Instant now) {
        token.setIsRevoked(true);
        token.setRevokedAt(now);
        token.setRevokedReason(reason);
        token.setLastUsedAt(now);
        refreshTokenRepository.save(token);
    }

    private boolean isUserActive(User user) {
        org.example.connectcg_be.security.UserPrincipal principal =
                org.example.connectcg_be.security.UserPrincipal.create(user);
        return principal.isEnabled() && principal.isAccountNonLocked();
    }

    private int currentAuthVersion(User user) {
        return user.getAuthVersion() == null ? 0 : user.getAuthVersion();
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String normalizeIp(String remoteAddress) {
        if (remoteAddress == null) {
            return null;
        }
        return remoteAddress.length() <= 45 ? remoteAddress : remoteAddress.substring(0, 45);
    }
}
