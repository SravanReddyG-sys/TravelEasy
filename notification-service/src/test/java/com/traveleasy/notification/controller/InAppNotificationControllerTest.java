package com.traveleasy.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.notification.model.InAppNotification;
import com.traveleasy.notification.model.RecipientRole;
import com.traveleasy.notification.repository.InAppNotificationRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class InAppNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InAppNotificationRepository inAppRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        inAppRepository.deleteAll();
    }

    private String createUserToken(Long userId, String email, String role) {
        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private InAppNotification createNotification(Long userId, String email, RecipientRole role, String title, boolean isRead) {
        return InAppNotification.builder()
                .eventId("EVT-" + UUID.randomUUID())
                .recipientUserId(userId)
                .recipientEmail(email)
                .recipientRole(role)
                .eventType("BUS_BOOKING_CONFIRMED")
                .title(title)
                .message("Test notification body")
                .actionUrl("/my-bookings")
                .isRead(isRead)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
    }

    @Test
    void testGetNotifications_UnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/notifications/in-app"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("UNAUTHORIZED"));
    }

    @Test
    void testGetNotifications_IsolationByUserAndRole() throws Exception {
        Long travellerId = 501L;
        Long operatorId = 601L;

        // Persist notifications for traveller and operator
        inAppRepository.save(createNotification(travellerId, "traveller501@traveleasy.com", RecipientRole.TRAVELLER, "Traveller Ticket 1", false));
        inAppRepository.save(createNotification(travellerId, "traveller501@traveleasy.com", RecipientRole.TRAVELLER, "Traveller Ticket 2", true));
        inAppRepository.save(createNotification(operatorId, "operator601@gsrtravels.com", RecipientRole.BUS_OPERATOR, "Operator Passenger Alert", false));

        String travellerToken = createUserToken(travellerId, "traveller501@traveleasy.com", "TRAVELLER");
        String operatorToken = createUserToken(operatorId, "operator601@gsrtravels.com", "BUS_OPERATOR");

        // Traveller only sees traveller notifications
        mockMvc.perform(get("/api/notifications/in-app")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + travellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].recipientUserId").value(travellerId))
                .andExpect(jsonPath("$[0].recipientRole").value("TRAVELLER"));

        // Operator only sees operator notifications
        mockMvc.perform(get("/api/notifications/in-app")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recipientUserId").value(operatorId))
                .andExpect(jsonPath("$[0].recipientRole").value("BUS_OPERATOR"));
    }

    @Test
    void testRoleMapping_RoleCustomerMapsToTraveller() throws Exception {
        Long customerId = 555L;
        inAppRepository.save(createNotification(customerId, "customer@traveleasy.com", RecipientRole.TRAVELLER, "Customer Booking", false));

        // Token minted by Auth Service with ROLE_CUSTOMER
        String customerToken = createUserToken(customerId, "customer@traveleasy.com", "ROLE_CUSTOMER");

        mockMvc.perform(get("/api/notifications/in-app")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recipientUserId").value(customerId))
                .andExpect(jsonPath("$[0].recipientRole").value("TRAVELLER"));

        mockMvc.perform(get("/api/notifications/in-app/unread-count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void testRoleMapping_RoleBusOperatorAndHotelManagerAndAdmin() throws Exception {
        Long opId = 666L;
        Long hmId = 777L;
        Long adminId = 888L;

        inAppRepository.save(createNotification(opId, "op@gsr.com", RecipientRole.BUS_OPERATOR, "Bus Alert", false));
        inAppRepository.save(createNotification(hmId, "hm@hotel.com", RecipientRole.HOTEL_PARTNER, "Hotel Alert", false));
        inAppRepository.save(createNotification(adminId, "admin@traveleasy.com", RecipientRole.ADMIN, "Admin Alert", false));

        // Tokens using Spring Security ROLE_ prefix
        String opToken = createUserToken(opId, "op@gsr.com", "ROLE_BUS_OPERATOR");
        String hmToken = createUserToken(hmId, "hm@hotel.com", "ROLE_HOTEL_MANAGER");
        String adminToken = createUserToken(adminId, "admin@traveleasy.com", "ROLE_ADMIN");

        mockMvc.perform(get("/api/notifications/in-app")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + opToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recipientRole").value("BUS_OPERATOR"));

        mockMvc.perform(get("/api/notifications/in-app")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recipientRole").value("HOTEL_PARTNER"));

        mockMvc.perform(get("/api/notifications/in-app")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recipientRole").value("ADMIN"));
    }

    @Test
    void testRoleMapping_UnknownRoleRejectedWithForbidden() throws Exception {
        String token = createUserToken(999L, "unknown@traveleasy.com", "ROLE_UNKNOWN");

        mockMvc.perform(get("/api/notifications/in-app")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"));
    }

    @Test
    void testGetUnreadCount_CorrectCountReturned() throws Exception {
        Long userId = 701L;
        inAppRepository.save(createNotification(userId, "user701@traveleasy.com", RecipientRole.TRAVELLER, "Unread 1", false));
        inAppRepository.save(createNotification(userId, "user701@traveleasy.com", RecipientRole.TRAVELLER, "Unread 2", false));
        inAppRepository.save(createNotification(userId, "user701@traveleasy.com", RecipientRole.TRAVELLER, "Already Read", true));

        String token = createUserToken(userId, "user701@traveleasy.com", "TRAVELLER");

        mockMvc.perform(get("/api/notifications/in-app/unread-count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    void testMarkAsRead_OwnershipEnforced_RejectsOtherUser() throws Exception {
        Long ownerId = 801L;
        Long intruderId = 802L;

        InAppNotification notif = inAppRepository.save(
                createNotification(ownerId, "owner801@traveleasy.com", RecipientRole.TRAVELLER, "Owner Note", false)
        );

        String intruderToken = createUserToken(intruderId, "intruder802@traveleasy.com", "ROLE_CUSTOMER");
        String ownerToken = createUserToken(ownerId, "owner801@traveleasy.com", "ROLE_CUSTOMER");

        // Intruder tries to mark owner's notification as read -> 403 Forbidden
        mockMvc.perform(put("/api/notifications/in-app/" + notif.getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"));

        // True owner marks notification as read -> 200 OK
        mockMvc.perform(put("/api/notifications/in-app/" + notif.getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true))
                .andExpect(jsonPath("$.readAt").exists());

        // Verify database state
        InAppNotification updated = inAppRepository.findById(notif.getId()).orElseThrow();
        assertTrue(updated.isRead());
    }

    @Test
    void testMarkAllAsRead_Success() throws Exception {
        Long userId = 901L;
        inAppRepository.save(createNotification(userId, "user901@traveleasy.com", RecipientRole.TRAVELLER, "Bulk 1", false));
        inAppRepository.save(createNotification(userId, "user901@traveleasy.com", RecipientRole.TRAVELLER, "Bulk 2", false));

        String token = createUserToken(userId, "user901@traveleasy.com", "ROLE_CUSTOMER");

        mockMvc.perform(put("/api/notifications/in-app/read-all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.markedRead").value(2));

        long unreadRemaining = inAppRepository.countByRecipientUserIdAndRecipientRoleAndIsReadFalse(userId, RecipientRole.TRAVELLER);
        assertEquals(0, unreadRemaining);
    }
}
