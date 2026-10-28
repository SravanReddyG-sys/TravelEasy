package com.traveleasy.bus.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;

@Service
public class BusSecurityService {

    @Value("${jwt.secret:TravelEasySuperSecretKeyForJWTSigning12345678901234567890}")
    private String secret;

    @PostConstruct
    public void validateConfiguration() {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("JWT signing key secret is not configured or too short (minimum 256 bits / 32 bytes required for HMAC-SHA256)");
        }
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long extractUserId(String token) {
        try {
            Claims claims = extractClaims(token);
            Object userIdObj = claims.get("userId");
            if (userIdObj instanceof Number) {
                long val = ((Number) userIdObj).longValue();
                return val > 0 ? val : null;
            } else if (userIdObj != null) {
                long val = Long.parseLong(userIdObj.toString().trim());
                return val > 0 ? val : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public String extractRole(String token) {
        try {
            Claims claims = extractClaims(token);
            Object role = claims.get("role");
            return role != null ? role.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public String extractEmail(String token) {
        try {
            return extractClaims(token).getSubject();
        } catch (Exception ignored) {
            return null;
        }
    }
}
