package org.example.connectcg_be.security;

public class RefreshTokenReuseException extends InvalidRefreshTokenException {
    private final String familyId;

    public RefreshTokenReuseException(String familyId) {
        super("Refresh token reuse detected");
        this.familyId = familyId;
    }

    public String getFamilyId() {
        return familyId;
    }
}
