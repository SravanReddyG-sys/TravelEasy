package com.traveleasy.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.notification.dto.EventContentDto;
import com.traveleasy.notification.dto.NotificationEventRequest;
import com.traveleasy.notification.dto.RecipientDto;
import com.traveleasy.notification.model.RecipientRole;
import com.traveleasy.notification.security.NotificationSecurityService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class EventIngestionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationSecurityService securityService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String createInternalToken(String serviceName, String role, String issuer, int validMinutes) {
        return Jwts.builder()
                .setSubject(serviceName)
                .claim("role", role)
                .setIssuer(issuer)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(validMinutes, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private NotificationEventRequest createSampleBookingRequest() {
        return NotificationEventRequest.builder()
                .eventId("SEC-TEST-" + UUID.randomUUID())
                .eventType("BUS_BOOKING_CONFIRMED")
                .aggregateType("BOOKING")
                .aggregateId("BK-TEST-001")
                .recipients(List.of(
                        RecipientDto.builder()
                                .userId(1L)
                                .email("test@traveleasy.com")
                                .role(RecipientRole.TRAVELLER)
                                .channels(List.of("IN_APP"))
                                .build()
                ))
                .content(EventContentDto.builder()
                        .title("Booking Confirmed")
                        .summary("Your booking is confirmed.")
                        .build())
                .build();
    }

    @Test
    void testFailSecureStartupSecretValidation() {
        NotificationSecurityService freshService = new NotificationSecurityService();
        ReflectionTestUtils.setField(freshService, "jwtSecret", "too-short-secret");
        assertThrows(IllegalStateException.class, freshService::validateSecurityConfiguration);

        ReflectionTestUtils.setField(freshService, "jwtSecret", null);
        assertThrows(IllegalStateException.class, freshService::validateSecurityConfiguration);
    }

    @Test
    void testMissingAuthorizationHeader_Returns401() throws Exception {
        NotificationEventRequest req = createSampleBookingRequest();

        mockMvc.perform(post("/api/notifications/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("UNAUTHORIZED"));
    }

    @Test
    void testInvalidTokenSignature_Returns401() throws Exception {
        NotificationEventRequest req = createSampleBookingRequest();

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.payload")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("UNAUTHORIZED"));
    }

    @Test
    void testWrongRoleOnToken_Returns403() throws Exception {
        NotificationEventRequest req = createSampleBookingRequest();
        String token = createInternalToken("booking-service", "USER", NotificationSecurityService.INTERNAL_ISSUER, 5);

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"));
    }

    @Test
    void testInvalidIssuer_Returns401() throws Exception {
        NotificationEventRequest req = createSampleBookingRequest();
        String token = createInternalToken("booking-service", NotificationSecurityService.ROLE_INTERNAL_SERVICE, "untrusted-issuer", 5);

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("UNAUTHORIZED"));
    }

    @Test
    void testDisallowedServiceIdentity_Returns403() throws Exception {
        NotificationEventRequest req = createSampleBookingRequest();
        String token = createInternalToken("untrusted-service", NotificationSecurityService.ROLE_INTERNAL_SERVICE, NotificationSecurityService.INTERNAL_ISSUER, 5);

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"));
    }

    @Test
    void testEventOwnershipViolation_PaymentServiceCannotSendBookingEvent_Returns403() throws Exception {
        NotificationEventRequest req = createSampleBookingRequest(); // BUS_BOOKING_CONFIRMED
        String token = createInternalToken("payment-service", NotificationSecurityService.ROLE_INTERNAL_SERVICE, NotificationSecurityService.INTERNAL_ISSUER, 5);

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"));
    }

    @Test
    void testEventOwnershipViolation_BookingServiceCannotSendRefundEvent_Returns403() throws Exception {
        NotificationEventRequest req = NotificationEventRequest.builder()
                .eventId("SEC-TEST-" + UUID.randomUUID())
                .eventType("PAYMENT_REFUND_COMPLETED")
                .aggregateType("PAYMENT")
                .aggregateId("TX-001")
                .recipients(List.of(
                        RecipientDto.builder()
                                .userId(1L)
                                .email("test@traveleasy.com")
                                .role(RecipientRole.TRAVELLER)
                                .channels(List.of("IN_APP"))
                                .build()
                ))
                .content(EventContentDto.builder()
                        .title("Refund Completed")
                        .summary("Refund completed.")
                        .build())
                .build();
        String token = createInternalToken("booking-service", NotificationSecurityService.ROLE_INTERNAL_SERVICE, NotificationSecurityService.INTERNAL_ISSUER, 5);

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"));
    }

    @Test
    void testBusService_CanSubmitBusScheduleCreated_Returns202() throws Exception {
        NotificationEventRequest req = NotificationEventRequest.builder()
                .eventId("BUS-SEC-TEST-" + UUID.randomUUID())
                .eventType("BUS_SCHEDULE_CREATED")
                .aggregateType("SCHEDULE")
                .aggregateId("SCHED-100")
                .recipients(List.of(
                        RecipientDto.builder()
                                .userId(202L)
                                .email("operator@test.com")
                                .role(RecipientRole.BUS_OPERATOR)
                                .channels(List.of("IN_APP"))
                                .build()
                ))
                .content(EventContentDto.builder()
                        .title("Schedule Created")
                        .summary("Schedule SCHED-100 created")
                        .build())
                .build();
        String token = createInternalToken("bus-service", NotificationSecurityService.ROLE_INTERNAL_SERVICE, NotificationSecurityService.INTERNAL_ISSUER, 5);

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("INGESTED"));
    }

    @Test
    void testBusService_CannotSubmitBookingEvent_Returns403() throws Exception {
        NotificationEventRequest req = createSampleBookingRequest(); // BUS_BOOKING_CONFIRMED
        String token = createInternalToken("bus-service", NotificationSecurityService.ROLE_INTERNAL_SERVICE, NotificationSecurityService.INTERNAL_ISSUER, 5);

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"));
    }

    @Test
    void testBookingService_CannotSubmitBusDeactivatedEvent_Returns403() throws Exception {
        NotificationEventRequest req = NotificationEventRequest.builder()
                .eventId("BUS-SEC-TEST-" + UUID.randomUUID())
                .eventType("BUS_DEACTIVATED")
                .aggregateType("BUS")
                .aggregateId("BUS-55")
                .recipients(List.of(
                        RecipientDto.builder()
                                .userId(202L)
                                .email("operator@test.com")
                                .role(RecipientRole.BUS_OPERATOR)
                                .channels(List.of("IN_APP"))
                                .build()
                ))
                .content(EventContentDto.builder()
                        .title("Bus Deactivated")
                        .summary("Bus 55 deactivated")
                        .build())
                .build();
        String token = createInternalToken("booking-service", NotificationSecurityService.ROLE_INTERNAL_SERVICE, NotificationSecurityService.INTERNAL_ISSUER, 5);

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"));
    }
}
