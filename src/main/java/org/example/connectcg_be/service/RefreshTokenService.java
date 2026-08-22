package org.example.connectcg_be.service;

import jakarta.servlet.http.HttpServletRequest;
import org.example.connectcg_be.entity.User;

import java.util.Optional;

public interface RefreshTokenService {
    IssuedRefreshToken issue(User user, HttpServletRequest request);

    IssuedRefreshToken rotate(String rawToken, HttpServletRequest request);

    Optional<String> revokeCurrent(String rawToken, String reason);

    void revokeAllForUser(Integer userId, String reason, boolean incrementAuthVersion);

    record IssuedRefreshToken(String rawToken, String familyId, User user) {
    }
}
