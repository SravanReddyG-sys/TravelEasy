package com.traveleasy.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.HotelCancellationRequest;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.HotelCancellationRefundTask;
import com.traveleasy.booking.model.HotelInventoryReleaseTask;
import com.traveleasy.booking.model.HotelReservationSnapshot;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.repository.HotelCancellationRefundTaskRepository;
import com.traveleasy.booking.repository.HotelInventoryReleaseTaskRepository;
import com.traveleasy.booking.repository.HotelReservationSnapshotRepository;
import com.traveleasy.booking.service.BookingInternalTokenService;
import com.traveleasy.booking.service.BookingSecurityService;
import com.traveleasy.booking.service.BookingStateTransitionService;
import com.traveleasy.booking.service.HotelCancellationOrchestrationService;
import com.traveleasy.booking.service.HotelCancellationPolicyService;
import com.traveleasy.booking.service.HotelCancellationRefundRecoveryService;
import com.traveleasy.booking.service.HotelCancellationStateService;
import com.traveleasy.booking.service.HotelInventoryReleaseRecoveryService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HotelCancellationController.class)
@Import({
        HotelCancellationOrchestrationService.class,
        HotelCancellationStateService.class,
        HotelCancellationPolicyService.class,
        BookingInternalTokenService.class
})
public class HotelCancellationControllerTest {

    private static final String TEST_JWT_SECRET = "TestSecretKeyForBookingServiceTesting1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private HotelReservationSnapshotRepository snapshotRepository;

    @MockBean
    private HotelInventoryReleaseTaskRepository releaseTaskRepository;

    @MockBean
    private HotelCancellationRefundTaskRepository refundTaskRepository;

    @MockBean
    private BookingStateTransitionService stateTransitionService;

    @MockBean
    private BookingSecurityService securityService;

    @MockBean
    private HotelInventoryReleaseRecoveryService inventoryReleaseRecoveryService;

    @MockBean
    private HotelCancellationRefundRecoveryService refundRecoveryService;

    private Booking sampleConfirmedHotelBooking() {
        return Booking.builder()
                .id(101L)
                .bookingRef("BKG-HTL-CTRL-101")
                .customerId(10L)
                .providerId(77L)
                .customerName("Aditi Rao")
                .customerEmail("aditi@example.com")
                .serviceType("HOTEL")
                .serviceId(501L)
                .totalAmount(5000.0)
                .status("CONFIRMED")
                .checkInDate(LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(5))
                .checkOutDate(LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(7))
                .detailsJson("{\"hotelName\":\"GSR Grand\",\"checkInDate\":\""
                        + LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(5)
                        + "\",\"checkOutDate\":\""
                        + LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(7) + "\"}")
                .build();
    }

    private String mintInternalToken(String serviceName) {
        Key key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .setSubject(serviceName)
                .setIssuer(BookingInternalTokenService.INTERNAL_ISSUER)
                .claim("role", BookingInternalTokenService.INTERNAL_SERVICE_ROLE)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 300_000L))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("GET & PUT hotel cancellation endpoints return 401 on missing token, invalid JWT, or bare X-Internal-Service")
    void testUnauthorizedRejections() throws Exception {
        Booking booking = sampleConfirmedHotelBooking();
        when(bookingRepository.findById(101L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByBookingRef("BKG-HTL-CTRL-101")).thenReturn(Optional.of(booking));
        when(bookingRepository.findByBookingRefForUpdate("BKG-HTL-CTRL-101")).thenReturn(Optional.of(booking));

        // Missing auth
        mockMvc.perform(get("/api/bookings/hotel/101/cancellation-quote"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/bookings/hotel/101/cancel"))
                .andExpect(status().isUnauthorized());

        // Invalid/expired user JWT
        when(securityService.validateToken("bad-token")).thenReturn(false);
        mockMvc.perform(get("/api/bookings/hotel/ref/BKG-HTL-CTRL-101/cancellation-quote")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());

        // Bare X-Internal-Service without signed X-Internal-Token
        mockMvc.perform(put("/api/bookings/hotel/ref/BKG-HTL-CTRL-101/cancel")
                        .header("X-Internal-Service", "payment-service"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Returns 403 for wrong traveller, wrong hotel manager, and ROLE_BUS_OPERATOR")
    void testForbiddenRejections() throws Exception {
        Booking booking = sampleConfirmedHotelBooking();
        when(bookingRepository.findById(101L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(booking));

        // Wrong customer
        when(securityService.validateToken("other-cust")).thenReturn(true);
        when(securityService.extractUserId("other-cust")).thenReturn(999L);
        when(securityService.extractRole("other-cust")).thenReturn("ROLE_CUSTOMER");
        mockMvc.perform(put("/api/bookings/hotel/101/cancel")
                        .header("Authorization", "Bearer other-cust"))
                .andExpect(status().isForbidden());

        // Wrong hotel manager
        when(securityService.validateToken("other-mgr")).thenReturn(true);
        when(securityService.extractUserId("other-mgr")).thenReturn(888L);
        when(securityService.extractRole("other-mgr")).thenReturn("ROLE_HOTEL_MANAGER");
        mockMvc.perform(get("/api/bookings/hotel/101/cancellation-quote")
                        .header("Authorization", "Bearer other-mgr"))
                .andExpect(status().isForbidden());

        // BUS operator
        when(securityService.validateToken("bus-op")).thenReturn(true);
        when(securityService.extractUserId("bus-op")).thenReturn(77L);
        when(securityService.extractRole("bus-op")).thenReturn("ROLE_BUS_OPERATOR");
        mockMvc.perform(put("/api/bookings/hotel/101/cancel")
                        .header("Authorization", "Bearer bus-op"))
                .andExpect(status().isForbidden());

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Returns 400 when BUS booking or invalid lifecycle status is passed to dedicated hotel endpoints")
    void testBusIsolationAndInvalidStatusesReturn400() throws Exception {
        when(securityService.validateToken("admin-tok")).thenReturn(true);
        when(securityService.extractUserId("admin-tok")).thenReturn(1L);
        when(securityService.extractRole("admin-tok")).thenReturn("ROLE_ADMIN");

        Booking busBooking = Booking.builder()
                .id(202L)
                .bookingRef("BKG-BUS-202")
                .customerId(10L)
                .serviceType("BUS")
                .serviceId(9L)
                .totalAmount(900.0)
                .status("CONFIRMED")
                .build();
        when(bookingRepository.findById(202L)).thenReturn(Optional.of(busBooking));
        when(bookingRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(busBooking));

        mockMvc.perform(get("/api/bookings/hotel/202/cancellation-quote")
                        .header("Authorization", "Bearer admin-tok"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(put("/api/bookings/hotel/202/cancel")
                        .header("Authorization", "Bearer admin-tok"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        for (String blockedStatus : new String[]{"CHECKED_IN", "CHECKED_OUT", "NO_SHOW", "EXPIRED", "PENDING_PAYMENT"}) {
            Booking b = sampleConfirmedHotelBooking();
            b.setId(300L);
            b.setStatus(blockedStatus);
            when(bookingRepository.findById(300L)).thenReturn(Optional.of(b));
            when(bookingRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(b));

            mockMvc.perform(get("/api/bookings/hotel/300/cancellation-quote")
                            .header("Authorization", "Bearer admin-tok"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(put("/api/bookings/hotel/300/cancel")
                            .header("Authorization", "Bearer admin-tok"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("Owning customer can quote by ID/ref and cancel by ID with 200 OK")
    void testOwningCustomerQuoteAndCancelById() throws Exception {
        Booking booking = sampleConfirmedHotelBooking();
        when(bookingRepository.findById(101L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByBookingRef("BKG-HTL-CTRL-101")).thenReturn(Optional.of(booking));
        when(snapshotRepository.findByBookingId(101L)).thenReturn(Optional.empty());
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(releaseTaskRepository.findByBookingId(101L)).thenReturn(Optional.empty());
        when(releaseTaskRepository.save(any(HotelInventoryReleaseTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refundTaskRepository.findByBookingId(101L)).thenReturn(Optional.empty());
        when(refundTaskRepository.save(any(HotelCancellationRefundTask.class))).thenAnswer(inv -> inv.getArgument(0));

        when(securityService.validateToken("cust-tok")).thenReturn(true);
        when(securityService.extractUserId("cust-tok")).thenReturn(10L);
        when(securityService.extractRole("cust-tok")).thenReturn("ROLE_CUSTOMER");

        // Quote by ref
        mockMvc.perform(get("/api/bookings/hotel/ref/BKG-HTL-CTRL-101/cancellation-quote")
                        .header("Authorization", "Bearer cust-tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingRef").value("BKG-HTL-CTRL-101"))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.policyTier").value(HotelCancellationPolicyService.TIER_FREE_48H))
                .andExpect(jsonPath("$.refundAmount").value(5000.0))
                .andExpect(jsonPath("$.policyTimeZone").value("Asia/Kolkata"));

        // Cancel by ID
        mockMvc.perform(put("/api/bookings/hotel/101/cancel")
                        .header("Authorization", "Bearer cust-tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HotelCancellationRequest("Plans changed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.idempotent").value(false))
                .andExpect(jsonPath("$.refundAmount").value(5000.0))
                .andExpect(jsonPath("$.refundAttemptKey").value("htl-rfnd-BKG-HTL-CTRL-101"));

        verify(inventoryReleaseRecoveryService).attemptImmediateRelease(any(HotelInventoryReleaseTask.class));
        verify(refundRecoveryService).attemptImmediateRefund(any(HotelCancellationRefundTask.class));
    }

    @Test
    @DisplayName("Owning hotel manager, ROLE_ADMIN, and strict internal JWT are authorized across quote/cancel by ref")
    void testManagerAdminAndStrictInternalCallerAuthorized() throws Exception {
        Booking bMgr = sampleConfirmedHotelBooking();
        when(bookingRepository.findByBookingRef("BKG-HTL-CTRL-101")).thenReturn(Optional.of(bMgr));
        when(bookingRepository.findByBookingRefForUpdate("BKG-HTL-CTRL-101")).thenReturn(Optional.of(bMgr));
        when(snapshotRepository.findByBookingId(101L)).thenReturn(Optional.of(HotelReservationSnapshot.builder()
                .bookingId(101L).bookingRef("BKG-HTL-CTRL-101").hotelId(501L).status("HELD").build()));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(releaseTaskRepository.findByBookingId(101L)).thenReturn(Optional.empty());
        when(releaseTaskRepository.save(any(HotelInventoryReleaseTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refundTaskRepository.findByBookingId(101L)).thenReturn(Optional.empty());
        when(refundTaskRepository.save(any(HotelCancellationRefundTask.class))).thenAnswer(inv -> inv.getArgument(0));

        // Owning hotel manager quotes by ref
        when(securityService.validateToken("mgr-tok")).thenReturn(true);
        when(securityService.extractUserId("mgr-tok")).thenReturn(77L);
        when(securityService.extractRole("mgr-tok")).thenReturn("ROLE_HOTEL_MANAGER");
        mockMvc.perform(get("/api/bookings/hotel/ref/BKG-HTL-CTRL-101/cancellation-quote")
                        .header("Authorization", "Bearer mgr-tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(101));

        // ROLE_ADMIN cancels by ref
        when(securityService.validateToken("admin-tok")).thenReturn(true);
        when(securityService.extractUserId("admin-tok")).thenReturn(1L);
        when(securityService.extractRole("admin-tok")).thenReturn("ROLE_ADMIN");
        mockMvc.perform(put("/api/bookings/hotel/ref/BKG-HTL-CTRL-101/cancel")
                        .header("Authorization", "Bearer admin-tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Strict internal JWT idempotently replays cancel by ref
        String internalJwt = mintInternalToken("payment-service");
        mockMvc.perform(put("/api/bookings/hotel/ref/BKG-HTL-CTRL-101/cancel")
                        .header("X-Internal-Token", internalJwt)
                        .header("X-Internal-Service", "payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotent").value(true))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
