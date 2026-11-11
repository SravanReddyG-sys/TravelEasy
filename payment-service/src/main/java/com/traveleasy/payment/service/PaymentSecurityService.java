package com.traveleasy.payment.service;

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
public class PaymentSecurityService {

    @Value("${jwt.secret:}")
    private String secret;

    @PostConstruct
    public void validateSecurityConfiguration() {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException(
                    "Security startup failure: jwt.secret is missing or insecure (must be at least 32 characters). Predictable defaults are strictly prohibited."
            );
        }
        log.info("PaymentSecurityService initialized with valid JWT secret.");
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

    /**
     * Validates a signed internal service JWT token against traveleasy-internal issuer,
     * INTERNAL_SERVICE role, and an explicit set of permitted calling services.
     */
    public boolean verifyStrictInternalToken(String authHeader,
                                             String internalTokenHeader,
                                             String internalServiceHeader,
                                             String... permittedServices) {
        String candidate = null;
        if (internalTokenHeader != null && !internalTokenHeader.isBlank()) {
            candidate = internalTokenHeader.trim();
        } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
            candidate = authHeader.substring(7).trim();
        }
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        if (candidate.startsWith("Bearer ")) {
            candidate = candidate.substring(7).trim();
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(candidate)
                    .getBody();
            if (!PaymentInternalTokenService.INTERNAL_ISSUER.equals(claims.getIssuer())) {
                return false;
            }
            Object role = claims.get("role");
            if (role == null || !PaymentInternalTokenService.INTERNAL_SERVICE_ROLE.equals(role.toString())) {
                return false;
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return false;
            }
            if (internalServiceHeader != null && !internalServiceHeader.isBlank()
                    && !subject.equalsIgnoreCase(internalServiceHeader.trim())) {
                return false;
            }
            if (permittedServices == null || permittedServices.length == 0) {
                return false;
            }
            String normalizedSubject = subject.trim().toLowerCase();
            for (String permitted : permittedServices) {
                if (permitted != null && permitted.trim().equalsIgnoreCase(normalizedSubject)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Rejected invalid internal token in payment-service: {}", e.getMessage());
            return false;
        }
    }
}
