package com.traveleasy.payment.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.Key;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentInternalTokenServiceTest {

    private PaymentInternalTokenService tokenService;
    private static final String TEST_SECRET = "TestSecretKeyForPaymentServiceTesting1234567890";

    @BeforeEach
    void setUp() {
        tokenService = new PaymentInternalTokenService();
        ReflectionTestUtils.setField(tokenService, "jwtSecret", TEST_SECRET);
        tokenService.validateSecret();
    }

    @Test
    @DisplayName("generateInternalToken creates valid signed JWT with expected internal claims")
    void testGenerateInternalToken_ValidClaims() {
        String token = tokenService.generateInternalToken();
        assertNotNull(token);
        assertFalse(token.isBlank());

        Key key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();

        assertEquals(PaymentInternalTokenService.SERVICE_NAME, claims.getSubject());
        assertEquals(PaymentInternalTokenService.INTERNAL_ISSUER, claims.getIssuer());
        assertEquals(PaymentInternalTokenService.INTERNAL_SERVICE_ROLE, claims.get("role"));
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().getTime() > System.currentTimeMillis());
    }

    @Test
    @DisplayName("generateInternalToken returns cached token on consecutive calls")
    void testGenerateInternalToken_Cached() {
        String token1 = tokenService.generateInternalToken();
        String token2 = tokenService.generateInternalToken();

        assertSame(token1, token2);
    }

    @Test
    @DisplayName("validateSecret throws IllegalStateException when secret is shorter than 32 characters")
    void testValidateSecret_TooShort_ThrowsException() {
        PaymentInternalTokenService insecureService = new PaymentInternalTokenService();
        ReflectionTestUtils.setField(insecureService, "jwtSecret", "short-secret");

        IllegalStateException ex = assertThrows(IllegalStateException.class, insecureService::validateSecret);
        assertTrue(ex.getMessage().contains("must be at least 32 characters"));
    }

    @Test
    @DisplayName("validateSecret throws IllegalStateException when secret is null or empty")
    void testValidateSecret_NullOrEmpty_ThrowsException() {
        PaymentInternalTokenService nullService = new PaymentInternalTokenService();
        ReflectionTestUtils.setField(nullService, "jwtSecret", "");

        assertThrows(IllegalStateException.class, nullService::validateSecret);
    }
}
