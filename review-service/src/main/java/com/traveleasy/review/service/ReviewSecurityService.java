package com.traveleasy.review.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;

@Slf4j
@Service
public class ReviewSecurityService {

    @Value("${jwt.secret:TravelEasySuperSecretKeyForJWTSigning12345678901234567890}")
    private String secret;

    @PostConstruct
    public void validateSecurityConfiguration() {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException(
                    "Security startup failure: jwt.secret is missing or insecure (must be at least 32 characters)."
            );
        }
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean validateToken(String token) {
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

    public String extractRole(String token) {
        Claims claims = extractClaims(token);
        Object role = claims.get("role");
        return role != null ? role.toString() : null;
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Claims claims = extractClaims(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number) {
            long val = ((Number) userIdObj).longValue();
            return val > 0 ? val : null;
        } else if (userIdObj != null) {
            try {
                long val = Long.parseLong(userIdObj.toString().trim());
                return val > 0 ? val : null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
