package com.traveleasy.traveller.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;

@Component
public class JwtUtil {

    @Value("${jwt.secret:}")
    private String secret;

    @PostConstruct
    public void validateConfiguration() {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("Security startup failure: jwt.secret is missing or insecure (must be at least 32 characters).");
        }
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        } else if (userIdObj != null) {
            return Long.parseLong(userIdObj.toString());
        }
        return null;
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        Object roleObj = claims.get("role");
        return roleObj != null ? roleObj.toString() : null;
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractFullName(String token) {
        Claims claims = extractAllClaims(token);
        Object nameObj = claims.get("fullName");
        return nameObj != null ? nameObj.toString() : null;
    }
}
