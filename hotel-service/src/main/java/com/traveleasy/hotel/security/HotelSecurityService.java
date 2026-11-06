package com.traveleasy.hotel.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Set;

@Slf4j
@Service
public class HotelSecurityService {

    public static final String INTERNAL_ISSUER = "traveleasy-internal";
    public static final String ROLE_INTERNAL_SERVICE = "INTERNAL_SERVICE";
    private static final Set<String> ALLOWED_INTERNAL_SERVICES = Set.of(
            "booking-service",
            "payment-service",
            "bus-service"
    );

    @Value("${jwt.secret:}")
    private String secret;

    @PostConstruct
    public void validateConfiguration() {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("JWT signing key secret is not configured or too short (minimum 256 bits / 32 bytes required for HMAC-SHA256). Predictable defaults are strictly prohibited.");
        }
        log.info("HotelSecurityService initialized with verified JWT signing key.");
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
            Claims claims = extractClaims(token);
            Object emailClaim = claims.get("email");
            if (emailClaim != null) {
                String emailStr = emailClaim.toString().trim();
                if (emailStr.contains("@")) {
                    return emailStr;
                }
            }
            String sub = claims.getSubject();
            if (sub != null && sub.contains("@")) {
                return sub.trim();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public String extractFullName(String token) {
        try {
            Claims claims = extractClaims(token);
            Object nameClaim = claims.get("fullName");
            if (nameClaim != null && !nameClaim.toString().isBlank()) {
                return nameClaim.toString().trim();
            }
            Object sub = claims.getSubject();
            return sub != null ? sub.toString().trim() : "Hotel Manager";
        } catch (Exception ignored) {
            return "Hotel Manager";
        }
    }

    /**
     * Authenticates a trusted internal microservice call.
     * Requires a cryptographically signed internal JWT token.
     * Can be passed either in 'Authorization: Bearer <token>' or 'X-Internal-Token: <token>'.
     * Validates:
     * 1. Token signature using the shared HMAC-SHA256 secret.
     * 2. Issuer matches 'traveleasy-internal'.
     * 3. Role matches 'INTERNAL_SERVICE'.
     * 4. Subject belongs to the allowed internal services list ('booking-service', etc.).
     * 5. If X-Internal-Service header is also provided, it must match the token subject.
     * Rejects callers passing an unverified or forged X-Internal-Service header without a valid signed token.
     */
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
            return false;
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

    public record CallerPrincipal(
            Long userId,
            String role,
            String email,
            String fullName,
            boolean isAdmin,
            boolean isHotelManager,
            boolean isCustomer
    ) {
        public boolean hasManagerOrAdminAccess() {
            return isAdmin || isHotelManager;
        }
    }

    public CallerPrincipal resolvePrincipal(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        if (!validateToken(token)) {
            return null;
        }
        Long userId = extractUserId(token);
        String role = extractRole(token);
        String email = extractEmail(token);
        String fullName = extractFullName(token);

        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(role);
        boolean isHotelManager = "ROLE_HOTEL_MANAGER".equalsIgnoreCase(role);
        boolean isCustomer = "ROLE_CUSTOMER".equalsIgnoreCase(role);

        return new CallerPrincipal(userId, role, email, fullName, isAdmin, isHotelManager, isCustomer);
    }
}
