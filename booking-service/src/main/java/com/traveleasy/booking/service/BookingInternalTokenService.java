package com.traveleasy.booking.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Slf4j
@Service
public class BookingInternalTokenService {

    public static final String INTERNAL_ISSUER = "traveleasy-internal";
    public static final String INTERNAL_SERVICE_ROLE = "INTERNAL_SERVICE";
    public static final String SERVICE_NAME = "booking-service";

    @Value("${jwt.secret:}")
    private String jwtSecret;

    private volatile String cachedToken;
    private volatile long cachedTokenExpiryMillis = 0L;

    @PostConstruct
    public void validateSecret() {
        if (jwtSecret == null || jwtSecret.trim().length() < 32) {
            throw new IllegalStateException(
                    "Security startup failure: jwt.secret is missing or insecure (must be at least 32 characters). Predictable defaults are strictly prohibited."
            );
        }
        log.info("BookingInternalTokenService initialized with valid JWT secret.");
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Mints a short-lived internal service token (5 minutes validity) and caches it.
     * Reuses the cached token until 60 seconds before its expiration.
     */
    public String generateInternalToken() {
        long nowMillis = System.currentTimeMillis();
        if (cachedToken != null && nowMillis < (cachedTokenExpiryMillis - 60_000L)) {
            return cachedToken;
        }

        synchronized (this) {
            if (cachedToken != null && nowMillis < (cachedTokenExpiryMillis - 60_000L)) {
                return cachedToken;
            }

            Date now = new Date(nowMillis);
            long expiryMillis = nowMillis + (5 * 60 * 1000L); // 5 minutes
            Date expiry = new Date(expiryMillis);

            String token = Jwts.builder()
                    .setSubject(SERVICE_NAME)
                    .setIssuer(INTERNAL_ISSUER)
                    .claim("role", INTERNAL_SERVICE_ROLE)
                    .setIssuedAt(now)
                    .setExpiration(expiry)
                    .signWith(getSigningKey())
                    .compact();

            this.cachedToken = token;
            this.cachedTokenExpiryMillis = expiryMillis;
            return token;
        }
    }

    /**
     * Strictly verifies an incoming internal microservice token (WP-HC-01).
     * Rejects callers that supply bare X-Internal-Service headers without a signed JWT,
     * expired/invalid signatures, non-internal issuers/roles, or mismatched X-Internal-Service headers.
     */
    public boolean verifyStrictInternalToken(String authHeader, String internalTokenHeader, String internalServiceHeader) {
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
            io.jsonwebtoken.Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (!INTERNAL_ISSUER.equals(claims.getIssuer())) {
                return false;
            }
            Object roleObj = claims.get("role");
            if (roleObj == null || !INTERNAL_SERVICE_ROLE.equals(roleObj.toString())) {
                return false;
            }
            String serviceName = claims.getSubject();
            if (serviceName == null || serviceName.isBlank()) {
                return false;
            }
            String normalizedService = serviceName.trim().toLowerCase();
            boolean allowed = "payment-service".equals(normalizedService)
                    || "hotel-service".equals(normalizedService)
                    || "booking-service".equals(normalizedService)
                    || "bus-service".equals(normalizedService);
            if (!allowed) {
                return false;
            }
            if (internalServiceHeader != null && !internalServiceHeader.isBlank()) {
                if (!serviceName.equalsIgnoreCase(internalServiceHeader.trim())) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            log.warn("Strict internal service JWT verification failed: {}", ex.getMessage());
            return false;
        }
    }
}
