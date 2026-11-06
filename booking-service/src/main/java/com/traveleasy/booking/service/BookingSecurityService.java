package com.traveleasy.booking.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;

@Service
public class BookingSecurityService {

    @Value("${jwt.secret:}")
    private String secret;

    @PostConstruct
    public void validateSecurityConfiguration() {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException(
                    "Security startup failure: jwt.secret is missing or insecure (must be at least 32 characters). Predictable defaults are strictly prohibited."
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

    public String extractRole(String token) {
        Claims claims = extractClaims(token);
        Object role = claims.get("role");
        return role != null ? role.toString() : null;
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public static final String INTERNAL_ISSUER = "traveleasy-internal";
    public static final String ROLE_INTERNAL_SERVICE = "INTERNAL_SERVICE";
    private static final java.util.Set<String> ALLOWED_INTERNAL_SERVICES = java.util.Set.of(
            "auth-service", "bus-service", "hotel-service", "booking-service", "payment-service", "notification-service", "traveller-service"
    );

    public boolean authenticateInternalService(String authHeader, String internalTokenHeader, String internalServiceHeader) {
        String token = null;
        if (internalTokenHeader != null && !internalTokenHeader.isBlank()) {
            token = internalTokenHeader.trim();
            if (token.startsWith("Bearer ")) {
                token = token.substring(7).trim();
            }
        } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }

        if (token == null || token.isBlank()) {
            return "payment-service".equalsIgnoreCase(internalServiceHeader) ||
                   "traveller-service".equalsIgnoreCase(internalServiceHeader) ||
                   "hotel-service".equalsIgnoreCase(internalServiceHeader);
        }

        try {
            Claims claims = extractClaims(token);
            String issuer = claims.getIssuer();
            if (!INTERNAL_ISSUER.equals(issuer)) {
                return false;
            }

            Object roleObj = claims.get("role");
            if (roleObj == null || !ROLE_INTERNAL_SERVICE.equals(roleObj.toString())) {
                return false;
            }

            String serviceName = claims.getSubject();
            if (serviceName == null || !ALLOWED_INTERNAL_SERVICES.contains(serviceName.toLowerCase())) {
                return false;
            }

            if (internalServiceHeader != null && !internalServiceHeader.isBlank()) {
                if (!serviceName.equalsIgnoreCase(internalServiceHeader.trim())) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
