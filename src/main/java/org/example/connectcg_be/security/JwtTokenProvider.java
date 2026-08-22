package org.example.connectcg_be.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenProvider {

    // Lấy secret key từ application.properties
    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationInMs:900000}")
    private long jwtExpirationInMs;

    // Tạo SecretKey chuẩn cho HS512
    private SecretKey getSigningKey() {
        // app.jwtSecret PHẢI là Base64 hợp lệ
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
        // Nếu lười config Base64, dùng: return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(UserPrincipal userPrincipal, String sessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(Integer.toString(userPrincipal.getId()))
                .claim("username", userPrincipal.getUsername())
                .claim("role", userPrincipal.getAuthorities().toString())
                .claim("sid", sessionId)
                .claim("ver", userPrincipal.getAuthVersion())
                .claim("token_type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    public Integer getUserIdFromJWT(String token) {
        return Integer.parseInt(parseClaims(token).getSubject());
    }

    public String getTokenId(String token) {
        return parseClaims(token).getId();
    }

    public String getSessionId(String token) {
        return parseClaims(token).get("sid", String.class);
    }

    public int getAuthVersion(String token) {
        Number version = parseClaims(token).get("ver", Number.class);
        return version == null ? -1 : version.intValue();
    }

    public Instant getExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public Duration getAccessTokenLifetime() {
        return Duration.ofMillis(jwtExpirationInMs);
    }

    public boolean validateToken(String authToken) {
        try {
            Claims claims = parseClaims(authToken);
            return claims.getId() != null
                    && claims.get("sid", String.class) != null
                    && "access".equals(claims.get("token_type", String.class))
                    && claims.get("ver", Number.class) != null;
        } catch (io.jsonwebtoken.security.SignatureException ex) {
            log.error("JWT signature không hợp lệ (secret verify không khớp secret đã ký).");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty.");
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
