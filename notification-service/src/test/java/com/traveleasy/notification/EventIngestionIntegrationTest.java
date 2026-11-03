package com.traveleasy.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.notification.dto.EventContentDto;
import com.traveleasy.notification.dto.NotificationEventRequest;
import com.traveleasy.notification.dto.RecipientDto;
import com.traveleasy.notification.model.InAppNotification;
import com.traveleasy.notification.model.NotificationDelivery;
import com.traveleasy.notification.model.ReceivedEvent;
import com.traveleasy.notification.model.RecipientRole;
import com.traveleasy.notification.repository.InAppNotificationRepository;
import com.traveleasy.notification.repository.NotificationDeliveryRepository;
import com.traveleasy.notification.repository.ReceivedEventRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class EventIngestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReceivedEventRepository receivedEventRepository;

    @Autowired
    private InAppNotificationRepository inAppNotificationRepository;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String createInternalToken(String serviceName) {
        return Jwts.builder()
                .setSubject(serviceName)
                .claim("role", NotificationSecurityService.ROLE_INTERNAL_SERVICE)
                .setIssuer(NotificationSecurityService.INTERNAL_ISSUER)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private NotificationEventRequest buildSampleBookingRequest(String eventId, String bookingRef) {
        return NotificationEventRequest.builder()
                .eventId(eventId)
                .eventType("BUS_BOOKING_CONFIRMED")
                .aggregateType("BOOKING")
                .aggregateId(bookingRef)
                .recipients(List.of(
                        RecipientDto.builder()
                                .role(RecipientRole.TRAVELLER)
                                .userId(101L)
                                .email("traveller101@traveleasy.com")
                                .name("Test Traveller")
                                .channels(List.of("IN_APP", "EMAIL"))
                                .build(),
                        RecipientDto.builder()
                                .role(RecipientRole.BUS_OPERATOR)
                                .userId(202L)
                                .email("operator202@gsrtravels.com")
                                .name("GSR Travels")
                                .channels(List.of("IN_APP"))
                                .build()
                ))
                .content(EventContentDto.builder()
                        .title("Booking Confirmed: Kadapa to Vijayawada")
                        .summary("Your booking BK-TEST is confirmed for seats U2A.")
                        .actionUrl("/my-bookings")
                        .template("bus-booking-confirmation")
                        .templateData(Map.of(
                                "bookingRef", bookingRef,
                                "origin", "Kadapa",
                                "destination", "Vijayawada",
                                "seats", "U2A",
                                "totalFare", 892.5
                        ))
                        .build())
                .build();
    }

    @Test
    void testSuccessfulBookingConfirmedIngestionAndSequentialIdempotency() throws Exception {
        String eventId = "EVT-SEQ-" + UUID.randomUUID();
        String bookingRef = "BK-BUS-SEQ-" + System.currentTimeMillis();
        String token = createInternalToken("booking-service");

        NotificationEventRequest req = buildSampleBookingRequest(eventId, bookingRef);

        // --- FIRST SUBMISSION: Expect 202 Accepted ---
        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("INGESTED"))
                .andExpect(jsonPath("$.eventId").value(eventId));

        // Verify Database Persistence
        Optional<ReceivedEvent> receivedOpt = receivedEventRepository.findByEventId(eventId);
        assertTrue(receivedOpt.isPresent(), "Event must be recorded in received_events table");
        assertEquals("booking-service", receivedOpt.get().getSourceService());
        assertEquals("BUS_BOOKING_CONFIRMED", receivedOpt.get().getEventType());
        assertEquals("INGESTED", receivedOpt.get().getStatus());

        // Verify In-App Notifications
        List<InAppNotification> inAppList = inAppNotificationRepository.findByEventId(eventId);
        assertEquals(2, inAppList.size(), "Must generate exactly 2 in-app notifications (1 for Traveller, 1 for Operator)");

        InAppNotification travellerInApp = inAppList.stream()
                .filter(n -> n.getRecipientRole() == RecipientRole.TRAVELLER)
                .findFirst().orElseThrow();
        assertEquals(101L, travellerInApp.getRecipientUserId());
        assertEquals("traveller101@traveleasy.com", travellerInApp.getRecipientEmail());
        assertFalse(travellerInApp.isRead());
        assertEquals("/my-bookings", travellerInApp.getActionUrl());

        InAppNotification operatorInApp = inAppList.stream()
                .filter(n -> n.getRecipientRole() == RecipientRole.BUS_OPERATOR)
                .findFirst().orElseThrow();
        assertEquals(202L, operatorInApp.getRecipientUserId());
        assertFalse(operatorInApp.isRead());

        // Verify Email Delivery Record
        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findByEventId(eventId);
        assertEquals(1, deliveries.size(), "Must generate exactly 1 external email delivery record for the Traveller");
        NotificationDelivery emailDelivery = deliveries.get(0);
        assertEquals("EMAIL", emailDelivery.getChannel());
        assertEquals("traveller101@traveleasy.com", emailDelivery.getRecipientEmail());
        assertEquals("PENDING", emailDelivery.getStatus());
        assertEquals("bus-booking-confirmation", emailDelivery.getTemplateName());
        assertNotNull(emailDelivery.getDeliveryRef());

        // --- SECOND SUBMISSION (IDEMPOTENCY): Expect 200 OK with ALREADY_INGESTED ---
        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_INGESTED"))
                .andExpect(jsonPath("$.eventId").value(eventId));

        // Verify No Duplicate Persistence
        List<InAppNotification> inAppAfter = inAppNotificationRepository.findByEventId(eventId);
        assertEquals(2, inAppAfter.size(), "Idempotent duplicate submission must NOT create duplicate in-app rows");

        List<NotificationDelivery> deliveriesAfter = notificationDeliveryRepository.findByEventId(eventId);
        assertEquals(1, deliveriesAfter.size(), "Idempotent duplicate submission must NOT create duplicate delivery rows");
    }

    @Test
    void testConcurrentEventIngestion_SafeHandlingAndSingleRecordGuarantee() throws Exception {
        String eventId = "EVT-CONCUR-" + UUID.randomUUID();
        String bookingRef = "BK-CONCUR-" + System.currentTimeMillis();
        String token = createInternalToken("booking-service");

        NotificationEventRequest req = buildSampleBookingRequest(eventId, bookingRef);
        String requestBody = objectMapper.writeValueAsString(req);

        int concurrency = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);

        AtomicInteger acceptedCount = new AtomicInteger(0);
        AtomicInteger alreadyIngestedCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to fire simultaneously
                    MvcResult result = mockMvc.perform(post("/api/notifications/events")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                            .andReturn();

                    int statusCode = result.getResponse().getStatus();
                    if (statusCode == 202) {
                        acceptedCount.incrementAndGet();
                    } else if (statusCode == 200) {
                        alreadyIngestedCount.incrementAndGet();
                    } else {
                        otherCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release the latch to start all requests concurrently
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent requests should finish within timeout");
        assertEquals(0, otherCount.get(), "Zero requests should fail with 500 or other errors");
        assertEquals(1, acceptedCount.get(), "Exactly one concurrent request must win and return 202 Accepted");
        assertEquals(concurrency - 1, alreadyIngestedCount.get(), "All other concurrent requests must receive 200 OK (ALREADY_INGESTED)");

        // Crucial: Verify that MySQL contains strictly ONE received_events row and NO duplicate children
        Optional<ReceivedEvent> receivedEvent = receivedEventRepository.findByEventId(eventId);
        assertTrue(receivedEvent.isPresent());

        List<InAppNotification> inAppRows = inAppNotificationRepository.findByEventId(eventId);
        assertEquals(2, inAppRows.size(), "Exactly 2 in-app notifications must exist across all concurrent attempts");

        List<NotificationDelivery> deliveryRows = notificationDeliveryRepository.findByEventId(eventId);
        assertEquals(1, deliveryRows.size(), "Exactly 1 delivery task must exist across all concurrent attempts");
    }

    @Test
    void testValidation_RejectUnsupportedEventType() throws Exception {
        String token = createInternalToken("booking-service");
        NotificationEventRequest req = buildSampleBookingRequest("VAL-1", "BK-1");
        req.setEventType("HOTEL_BOOKING_CONFIRMED"); // Unsupported in Stage 1

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unsupported or missing eventType")));
    }

    @Test
    void testValidation_RejectUnsupportedDeliveryChannel() throws Exception {
        String token = createInternalToken("booking-service");
        NotificationEventRequest req = buildSampleBookingRequest("VAL-2", "BK-2");
        req.getRecipients().get(0).setChannels(List.of("SMS")); // SMS is out of scope

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unsupported delivery channel")));
    }

    @Test
    void testValidation_RejectInvalidEmailFormatForEmailChannel() throws Exception {
        String token = createInternalToken("booking-service");
        NotificationEventRequest req = buildSampleBookingRequest("VAL-3", "BK-3");
        req.getRecipients().get(0).setEmail("not-an-email");

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Valid email address is required")));
    }

    @Test
    void testValidation_RejectUnsafeActionUrl() throws Exception {
        String token = createInternalToken("booking-service");
        NotificationEventRequest req = buildSampleBookingRequest("VAL-4", "BK-4");
        req.getContent().setActionUrl("https://malicious-site.com/steal-creds"); // Non-relative external scheme

        mockMvc.perform(post("/api/notifications/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("actionUrl must be a safe relative path")));
    }
}
