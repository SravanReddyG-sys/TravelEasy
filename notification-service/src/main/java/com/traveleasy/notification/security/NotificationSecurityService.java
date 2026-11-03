package com.traveleasy.notification.security;

import com.traveleasy.notification.model.RecipientRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class NotificationSecurityService {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    public static final String INTERNAL_ISSUER = "traveleasy-internal";
    public static final String ROLE_INTERNAL_SERVICE = "INTERNAL_SERVICE";

    private static final Set<String> ALLOWED_INTERNAL_SERVICES = Set.of(
            "booking-service",
            "payment-service",
            "bus-service"
    );

    private static final Map<String, Set<String>> SERVICE_EVENT_OWNERSHIP = Map.of(
            "booking-service", Set.of("BUS_BOOKING_CONFIRMED", "BUS_BOOKING_CANCELLED"),
            "payment-service", Set.of("PAYMENT_FAILED", "PAYMENT_REFUND_INITIATED", "PAYMENT_REFUND_COMPLETED"),
            "bus-service", Set.of("BUS_SCHEDULE_CREATED", "BUS_SCHEDULE_CANCELLED", "BUS_DEACTIVATED", "BUS_ACTIVATED")
    );

    @PostConstruct
    public void validateSecurityConfiguration() {
        if (jwtSecret == null || jwtSecret.trim().length() < 32) {
            throw new IllegalStateException(
                    "Security startup failure: jwt.secret is missing or insecure (must be at least 32 characters). Predictable defaults are strictly prohibited."
            );
        }
        log.info("NotificationSecurityService initialized with fail-secure JWT secret verification.");
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates an internal service JWT token and verifies service identity and event ownership.
     */
    public String authenticateInternalService(String authHeader, String eventType) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SecurityException("Missing or malformed Authorization header for internal service request.");
        }

        String token = authHeader.substring(7).trim();
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception ex) {
            log.warn("Internal service JWT signature verification failed: {}", ex.getMessage());
            throw new SecurityException("Invalid or expired internal service JWT: " + ex.getMessage());
        }

        // Validate issuer
        String issuer = claims.getIssuer();
        if (!INTERNAL_ISSUER.equals(issuer)) {
            log.warn("Invalid internal service JWT issuer: {}", issuer);
            throw new SecurityException("Invalid internal service token issuer.");
        }

        // Validate role
        Object roleObj = claims.get("role");
        String role = roleObj != null ? roleObj.toString() : null;
        if (!ROLE_INTERNAL_SERVICE.equals(role)) {
            log.warn("Unauthorized role on internal service token: {}", role);
            throw new SecurityException("Forbidden: Token does not possess INTERNAL_SERVICE role.");
        }

        // Validate subject (service identity allowlist)
        String serviceName = claims.getSubject();
        if (serviceName == null || !ALLOWED_INTERNAL_SERVICES.contains(serviceName)) {
            log.warn("Service identity '{}' is not in the internal service allowlist.", serviceName);
            throw new SecurityException("Forbidden: Service identity '" + serviceName + "' is not permitted to ingest events.");
        }

        // Enforce Event Ownership against authenticated service identity
        Set<String> permittedEvents = SERVICE_EVENT_OWNERSHIP.get(serviceName);
        if (permittedEvents == null || !permittedEvents.contains(eventType)) {
            log.warn("Event ownership violation: Service '{}' is not authorized to submit event type '{}'.", serviceName, eventType);
            throw new SecurityException("Forbidden: Service '" + serviceName + "' does not own event type '" + eventType + "'.");
        }

        return serviceName;
    }

    /**
     * Validates a user JWT and extracts authenticated claims.
     */
    public Claims validateUserToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SecurityException("Missing or malformed Authorization header.");
        }
        String token = authHeader.substring(7).trim();
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception ex) {
            log.warn("User JWT verification failed: {}", ex.getMessage());
            throw new SecurityException("Invalid or expired user token: " + ex.getMessage());
        }
    }

    public Long extractUserId(Claims claims) {
        if (claims == null) return null;
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        } else if (userIdObj != null) {
            try {
                return Long.parseLong(userIdObj.toString().trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public RecipientRole extractRole(Claims claims) {
        if (claims == null) return null;
        Object roleObj = claims.get("role");
        if (roleObj == null) return null;
        String roleStr = roleObj.toString().trim().toUpperCase();
        try {
            return RecipientRole.valueOf(roleStr);
        } catch (IllegalArgumentException ex) {
            // Check common aliases e.g. "ROLE_TRAVELLER" -> "TRAVELLER"
            if (roleStr.startsWith("ROLE_")) {
                try {
                    return RecipientRole.valueOf(roleStr.substring(5));
                } catch (IllegalArgumentException ignored) {
                }
            }
            log.warn("Unrecognized role claim in JWT: {}", roleStr);
            return null;
        }
    }

    public String extractEmail(Claims claims) {
        if (claims == null) return null;
        return claims.getSubject();
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
