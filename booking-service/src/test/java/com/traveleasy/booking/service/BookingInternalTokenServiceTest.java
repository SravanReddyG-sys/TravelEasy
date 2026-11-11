package com.traveleasy.booking.service;

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

public class BookingInternalTokenServiceTest {

    private BookingInternalTokenService tokenService;
    private static final String TEST_SECRET = "TestSecretKeyForBookingServiceTesting1234567890";

    @BeforeEach
    void setUp() {
        tokenService = new BookingInternalTokenService();
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

        assertEquals(BookingInternalTokenService.SERVICE_NAME, claims.getSubject());
        assertEquals(BookingInternalTokenService.INTERNAL_ISSUER, claims.getIssuer());
        assertEquals(BookingInternalTokenService.INTERNAL_SERVICE_ROLE, claims.get("role"));
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
        BookingInternalTokenService insecureService = new BookingInternalTokenService();
        ReflectionTestUtils.setField(insecureService, "jwtSecret", "short-secret");

        IllegalStateException ex = assertThrows(IllegalStateException.class, insecureService::validateSecret);
        assertTrue(ex.getMessage().contains("must be at least 32 characters"));
    }

    @Test
    @DisplayName("validateSecret throws IllegalStateException when secret is null or empty")
    void testValidateSecret_NullOrEmpty_ThrowsException() {
        BookingInternalTokenService nullService = new BookingInternalTokenService();
        ReflectionTestUtils.setField(nullService, "jwtSecret", "");

        assertThrows(IllegalStateException.class, nullService::validateSecret);
    }

    @Test
    @DisplayName("verifyStrictInternalToken accepts ONLY signed booking-service internal JWT via X-Internal-Token or Bearer Authorization")
    void testVerifyStrictInternalToken_ValidInternalJwt() {
        String bookingToken = tokenService.generateInternalToken();

        assertTrue(tokenService.verifyStrictInternalToken(null, bookingToken, "booking-service"));
        assertTrue(tokenService.verifyStrictInternalToken(null, "Bearer " + bookingToken, "BOOKING-SERVICE"));
        assertTrue(tokenService.verifyStrictInternalToken("Bearer " + bookingToken, null, "booking-service"));
        assertTrue(tokenService.verifyStrictInternalToken("Bearer " + bookingToken, null, null));
    }

    @Test
    @DisplayName("verifyStrictInternalToken rejects disallowed services (payment/hotel/bus), bare headers, mismatched service, wrong issuer/role, expired, and forged JWTs")
    void testVerifyStrictInternalToken_Rejections() {
        Key key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Key wrongKey = Keys.hmacShaKeyFor("AnotherCompletelyDifferentSecretKey9876543210".getBytes(StandardCharsets.UTF_8));
        java.util.Date now = new java.util.Date();
        java.util.Date exp = new java.util.Date(now.getTime() + 300_000L);

        // Bare X-Internal-Service without JWT
        assertFalse(tokenService.verifyStrictInternalToken(null, null, "booking-service"));
        assertFalse(tokenService.verifyStrictInternalToken(null, "   ", "hotel-service"));

        // Non-booking internal services with otherwise validly signed internal JWTs MUST be rejected
        for (String disallowedService : new String[]{"payment-service", "hotel-service", "bus-service", "evil-service"}) {
            String otherInternalToken = Jwts.builder()
                    .setSubject(disallowedService)
                    .setIssuer(BookingInternalTokenService.INTERNAL_ISSUER)
                    .claim("role", BookingInternalTokenService.INTERNAL_SERVICE_ROLE)
                    .setIssuedAt(now)
                    .setExpiration(exp)
                    .signWith(key)
                    .compact();
            assertFalse(tokenService.verifyStrictInternalToken(null, otherInternalToken, disallowedService),
                    "Should reject disallowed service: " + disallowedService);
        }

        // Mismatched X-Internal-Service vs token subject
        assertFalse(tokenService.verifyStrictInternalToken(null, tokenService.generateInternalToken(), "payment-service"));

        // Customer JWT masquerading as internal service
        String customerToken = Jwts.builder()
                .setSubject("customer@example.com")
                .setIssuer("traveleasy-auth")
                .claim("role", "ROLE_CUSTOMER")
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key)
                .compact();
        assertFalse(tokenService.verifyStrictInternalToken(null, customerToken, "booking-service"));

        // Wrong issuer even with INTERNAL_SERVICE role
        String badIssuerToken = Jwts.builder()
                .setSubject("booking-service")
                .setIssuer("untrusted-issuer")
                .claim("role", BookingInternalTokenService.INTERNAL_SERVICE_ROLE)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key)
                .compact();
        assertFalse(tokenService.verifyStrictInternalToken(null, badIssuerToken, "booking-service"));

        // Forged signature
        String forgedToken = Jwts.builder()
                .setSubject("booking-service")
                .setIssuer(BookingInternalTokenService.INTERNAL_ISSUER)
                .claim("role", BookingInternalTokenService.INTERNAL_SERVICE_ROLE)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(wrongKey)
                .compact();
        assertFalse(tokenService.verifyStrictInternalToken(null, forgedToken, "booking-service"));

        // Expired token
        String expiredToken = Jwts.builder()
                .setSubject("booking-service")
                .setIssuer(BookingInternalTokenService.INTERNAL_ISSUER)
                .claim("role", BookingInternalTokenService.INTERNAL_SERVICE_ROLE)
                .setIssuedAt(new java.util.Date(now.getTime() - 120_000L))
                .setExpiration(new java.util.Date(now.getTime() - 60_000L))
                .signWith(key)
                .compact();
        assertFalse(tokenService.verifyStrictInternalToken(null, expiredToken, "booking-service"));
    }
}
