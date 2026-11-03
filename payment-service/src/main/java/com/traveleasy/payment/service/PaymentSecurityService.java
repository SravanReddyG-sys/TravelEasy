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
}
