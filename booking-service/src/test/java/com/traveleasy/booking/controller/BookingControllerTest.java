package com.traveleasy.booking.controller;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.service.BookingExpiryReconciliationService;
import com.traveleasy.booking.service.BookingSecurityService;
import com.traveleasy.booking.service.ScheduleCancellationCoordinatorService;
import com.traveleasy.booking.service.BookingStateTransitionService;
import com.traveleasy.booking.model.ScheduleCancellationBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
public class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingController bookingController;

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private BookingSecurityService securityService;

    @MockBean
    private BookingExpiryReconciliationService expiryService;

    @MockBean
    private ScheduleCancellationCoordinatorService coordinatorService;

    @MockBean
    private BookingStateTransitionService stateTransitionService;

    @MockBean
    private com.traveleasy.booking.service.BookingInternalTokenService internalTokenService;

    @MockBean
    private com.traveleasy.booking.service.HotelCancellationPolicyService cancellationPolicyService;

    @MockBean
    private com.traveleasy.booking.service.HotelGuestLifecycleService guestLifecycleService;

    @MockBean
    private com.traveleasy.booking.service.HotelBookingOrchestrationService hotelOrchestrationService;

    @MockBean
    private com.traveleasy.booking.repository.HotelReservationSnapshotRepository snapshotRepository;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        bookingController.setRestTemplate(restTemplate);
        when(stateTransitionService.confirmBooking(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setStatus("CONFIRMED");
            return b;
        });
        when(stateTransitionService.cancelBooking(any(Booking.class), any(), any())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setStatus("CANCELLED");
            return b;
        });
    }

    @Test
    @DisplayName("POST /api/bookings - Creates booking with PENDING_PAYMENT status")
    void testCreateBooking_Success() throws Exception {
        Booking request = Booking.builder()
                .customerId(10L)
                .customerName("John Doe")
                .customerEmail("john@example.com")
                .serviceType("BUS")
                .serviceId(1L)
                .totalAmount(523.95)
                .detailsJson("Bus: Express | Seats: 3B")
                .build();

        Booking saved = Booking.builder()
                .id(1L)
                .bookingRef("BKG-TEST1234")
                .customerId(10L)
                .customerName("John Doe")
                .customerEmail("john@example.com")
                .serviceType("BUS")
                .serviceId(1L)
                .totalAmount(523.95)
                .status("PENDING_PAYMENT")
                .detailsJson("Bus: Express | Seats: 3B")
                .bookingDate(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        when(bookingRepository.save(any(Booking.class))).thenReturn(saved);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.bookingRef").value("BKG-TEST1234"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(523.95));
    }

    @Test
    @DisplayName("POST /api/bookings - Rejects booking creation when caller is ROLE_BUS_OPERATOR")
    void testCreateBooking_RoleBusOperator_Forbidden() throws Exception {
        Booking request = Booking.builder()
                .serviceType("BUS")
                .serviceId(1L)
                .totalAmount(450.0)
                .build();

        String token = "valid-operator-token";
        when(securityService.validateToken(token)).thenReturn(true);
        when(securityService.extractRole(token)).thenReturn("ROLE_BUS_OPERATOR");

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Management accounts are prohibited from booking customer tickets."));

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("POST /api/bookings - Rejects booking creation when caller is ROLE_HOTEL_MANAGER")
    void testCreateBooking_RoleHotelManager_Forbidden() throws Exception {
        Booking request = Booking.builder()
                .serviceType("HOTEL")
                .serviceId(2L)
                .totalAmount(1200.0)
                .build();

        String token = "valid-manager-token";
        when(securityService.validateToken(token)).thenReturn(true);
        when(securityService.extractRole(token)).thenReturn("ROLE_HOTEL_MANAGER");

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Management accounts are prohibited from booking customer tickets."));

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("POST /api/bookings - Allows booking creation when caller is ROLE_CUSTOMER and populates identity")
    void testCreateBooking_RoleCustomer_Allowed() throws Exception {
        Booking request = Booking.builder()
                .serviceType("BUS")
                .serviceId(1L)
                .totalAmount(650.0)
                .build();

        Booking saved = Booking.builder()
                .id(2L)
                .bookingRef("BKG-CUST1234")
                .customerId(99L)
                .customerEmail("traveller@example.com")
                .serviceType("BUS")
                .serviceId(1L)
                .totalAmount(650.0)
                .status("PENDING_PAYMENT")
                .build();

        String token = "valid-customer-token";
        when(securityService.validateToken(token)).thenReturn(true);
        when(securityService.extractRole(token)).thenReturn("ROLE_CUSTOMER");
        when(securityService.extractUserId(token)).thenReturn(99L);
        when(securityService.extractEmail(token)).thenReturn("traveller@example.com");
        when(bookingRepository.save(any(Booking.class))).thenReturn(saved);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.customerId").value(99))
                .andExpect(jsonPath("$.customerEmail").value("traveller@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        verify(bookingRepository, times(1)).save(any(Booking.class));
    }

    @Test
    @DisplayName("POST /api/bookings - Rejects booking creation with 401 when token is invalid or expired")
    void testCreateBooking_InvalidToken_Unauthorized() throws Exception {
        Booking request = Booking.builder()
                .serviceType("BUS")
                .serviceId(1L)
                .totalAmount(500.0)
                .build();

        String token = "bad-or-expired-token";
        when(securityService.validateToken(token)).thenReturn(false);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired authorization token"));

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("PUT /api/bookings/{id}/confirm - Rejects when payment verification fails in payment-service")
    void testConfirmBooking_PaymentVerificationFailed() throws Exception {
        Booking pendingBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-UNPAID")
                .customerId(10L)
                .totalAmount(500.0)
                .status("PENDING_PAYMENT")
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pendingBooking));

        // Mock payment service returning FAILED status
        when(restTemplate.getForEntity(contains("/api/payments/booking/BKG-UNPAID"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "FAILED", "amount", 500.0), HttpStatus.OK));

        mockMvc.perform(put("/api/bookings/1/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Authoritative payment verification failed")));

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("PUT /api/bookings/{id}/confirm - Rejects when payment amount does not match booking amount")
    void testConfirmBooking_AmountMismatchRejected() throws Exception {
        Booking pendingBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-TAMPER")
                .customerId(10L)
                .totalAmount(1000.0)
                .status("PENDING_PAYMENT")
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pendingBooking));

        // Mock payment service returning SUCCESS but for tampered amount 100.0 instead of 1000.0
        when(restTemplate.getForEntity(contains("/api/payments/booking/BKG-TAMPER"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "SUCCESS", "amount", 100.0), HttpStatus.OK));

        mockMvc.perform(put("/api/bookings/1/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Authoritative payment verification failed")));

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("PUT /api/bookings/{id}/confirm - Confirms booking and locks seats when payment is SUCCESS")
    void testConfirmBooking_PaymentSuccess() throws Exception {
        Booking pendingBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-SUCCESS")
                .customerId(10L)
                .serviceType("BUS")
                .serviceId(5L)
                .totalAmount(500.0)
                .detailsJson("Route: HYD-VJA | Seats: 3B, 3C")
                .status("PENDING_PAYMENT")
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pendingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Payment verified SUCCESS
        when(restTemplate.getForEntity(contains("/api/payments/booking/BKG-SUCCESS"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "SUCCESS", "amount", 500.0), HttpStatus.OK));

        // Bus service confirm seats
        when(restTemplate.exchange(contains("/api/buses/schedules/5/confirm-seats"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("message", "Seats confirmed"), HttpStatus.OK));

        mockMvc.perform(put("/api/bookings/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Booking confirmed"))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));

        verify(restTemplate).exchange(contains("/api/buses/schedules/5/confirm-seats"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("PUT /api/bookings/{id}/confirm - Race condition: seats already taken triggers refund and returns 409")
    void testConfirmBooking_SeatsConflict_TriggersRefund() throws Exception {
        Booking pendingBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-LATE-RACE")
                .customerId(10L)
                .serviceType("BUS")
                .serviceId(5L)
                .totalAmount(500.0)
                .detailsJson("Route: HYD-VJA | Seats: 3B")
                .status("PENDING_PAYMENT")
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pendingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Payment verified SUCCESS
        when(restTemplate.getForEntity(contains("/api/payments/booking/BKG-LATE-RACE"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "SUCCESS", "amount", 500.0), HttpStatus.OK));

        // Bus service rejects seat confirmation with 409 CONFLICT (allocated to someone else)
        when(restTemplate.exchange(contains("/api/buses/schedules/5/confirm-seats"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("message", "Seats no longer available"), HttpStatus.CONFLICT));

        // Payment refund mock
        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-LATE-RACE"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("refundId", "rfnd_AUTO_123", "status", "SUCCESS"), HttpStatus.OK));

        mockMvc.perform(put("/api/bookings/1/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Seat hold has expired and seats have been allocated to another traveller")))
                .andExpect(jsonPath("$.booking.status").value("EXPIRED"));

        verify(restTemplate).exchange(contains("/api/payments/refund/booking/BKG-LATE-RACE"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("PUT /api/bookings/{id}/confirm - Idempotent when booking is already CONFIRMED")
    void testConfirmBooking_AlreadyConfirmed_Idempotent() throws Exception {
        Booking confirmedBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-ALREADY-CONFIRMED")
                .customerId(10L)
                .status("CONFIRMED")
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(confirmedBooking));

        mockMvc.perform(put("/api/bookings/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Booking is already confirmed"))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));

        verify(restTemplate, never()).getForEntity(anyString(), any());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("PUT /api/bookings/{id}/confirm - Forbids customer A from confirming customer B's booking")
    void testConfirmBooking_Forbidden_OtherUser() throws Exception {
        Booking customer10Booking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-CUST10")
                .customerId(10L)
                .status("PENDING_PAYMENT")
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(customer10Booking));

        when(securityService.validateToken("token-user-99")).thenReturn(true);
        when(securityService.extractUserId("token-user-99")).thenReturn(99L);
        when(securityService.extractRole("token-user-99")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(put("/api/bookings/1/confirm")
                        .header("Authorization", "Bearer token-user-99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Cannot confirm another user's booking")));
    }

    @Test
    @DisplayName("PUT /api/bookings/ref/{bookingRef}/expire - Authoritatively expires an unpaid booking")
    void testExpireBooking_Success() throws Exception {
        when(expiryService.expireBookingByRef("BKG-EXPIRE-NOW")).thenReturn(true);

        mockMvc.perform(put("/api/bookings/ref/BKG-EXPIRE-NOW/expire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingRef").value("BKG-EXPIRE-NOW"))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.expired").value(true));

        verify(expiryService).expireBookingByRef("BKG-EXPIRE-NOW");
    }

    @Test
    @DisplayName("PUT /api/bookings/{id}/cancel - Releases seats in bus-service and triggers refund in payment-service")
    void testCancelBooking_Success() throws Exception {
        Booking confirmedBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-CANCEL-ME")
                .customerId(10L)
                .serviceType("BUS")
                .serviceId(2L)
                .totalAmount(450.0)
                .detailsJson("Bus: Express | Seats: 4A, 4B")
                .status("CONFIRMED")
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(confirmedBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        when(restTemplate.exchange(contains("/api/buses/schedules/2/release-seats"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("message", "Seats released"), HttpStatus.OK));

        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-CANCEL-ME"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("refundId", "rfnd_TEST999", "status", "SUCCESS", "amount", 450.0), HttpStatus.OK));

        mockMvc.perform(put("/api/bookings/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("Booking cancelled successfully")))
                .andExpect(jsonPath("$.booking.status").value("CANCELLED"))
                .andExpect(jsonPath("$.booking.refundStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.booking.refundId").value("rfnd_TEST999"))
                .andExpect(jsonPath("$.booking.refundAmount").value(450.0))
                .andExpect(jsonPath("$.releasedSeats[0]").value("4A"))
                .andExpect(jsonPath("$.releasedSeats[1]").value("4B"))
                .andExpect(jsonPath("$.refund.refundId").value("rfnd_TEST999"));

        verify(restTemplate).exchange(contains("/api/buses/schedules/2/release-seats"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class));
        verify(restTemplate).exchange(contains("/api/payments/refund/booking/BKG-CANCEL-ME"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("PUT /api/bookings/{id}/cancel - Rejects cancellation if already CANCELLED and PROCESSED")
    void testCancelBooking_AlreadyCancelled() throws Exception {
        Booking cancelledBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-ALREADY-CANCELLED")
                .customerId(10L)
                .status("CANCELLED")
                .refundStatus("PROCESSED")
                .refundId("rfnd_done_123")
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(cancelledBooking));

        mockMvc.perform(put("/api/bookings/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("refund has already been processed")));

        verify(restTemplate, never()).exchange(contains("release-seats"), any(), any(), eq(Map.class));
        verify(restTemplate, never()).exchange(contains("refund"), any(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("PUT /api/bookings/{id}/cancel - Successfully coordinates refund on previously cancelled booking with uncompleted refund")
    void testCancelBooking_AlreadyCancelled_RetriesPendingRefund() throws Exception {
        Booking cancelledBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-RETRY-REFUND")
                .customerId(10L)
                .status("CANCELLED")
                .refundStatus("FAILED_RETRYABLE")
                .totalAmount(500.0)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(cancelledBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-RETRY-REFUND"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("refundId", "rfnd_RECOVERED", "refundStatus", "PROCESSED", "refundAmount", 500.0), HttpStatus.OK));

        mockMvc.perform(put("/api/bookings/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("Refund processed successfully")))
                .andExpect(jsonPath("$.booking.refundStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.booking.refundId").value("rfnd_RECOVERED"))
                .andExpect(jsonPath("$.refund.refundId").value("rfnd_RECOVERED"));

        verify(restTemplate).exchange(contains("/api/payments/refund/booking/BKG-RETRY-REFUND"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("PUT /api/bookings/{id}/cancel - Forbids customer A from cancelling customer B's booking")
    void testCancelBooking_Forbidden_OtherUser() throws Exception {
        Booking customer10Booking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-CUST10")
                .customerId(10L)
                .status("CONFIRMED")
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(customer10Booking));

        when(securityService.validateToken("token-user-99")).thenReturn(true);
        when(securityService.extractUserId("token-user-99")).thenReturn(99L);
        when(securityService.extractRole("token-user-99")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(put("/api/bookings/1/cancel")
                        .header("Authorization", "Bearer token-user-99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Cannot cancel another user's booking")));
    }

    @Test
    @DisplayName("GET /api/bookings/customer/{customerId} - Forbids customer A from viewing customer B's booking list")
    void testGetCustomerBookings_Forbidden_OtherUser() throws Exception {
        when(securityService.validateToken("token-user-99")).thenReturn(true);
        when(securityService.extractUserId("token-user-99")).thenReturn(99L);
        when(securityService.extractRole("token-user-99")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/bookings/customer/10")
                        .header("Authorization", "Bearer token-user-99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Access denied to customer bookings")));
    }

    @Test
    @DisplayName("GET /api/bookings/customer/{customerId} - Lazily expires stale PENDING_PAYMENT bookings and excludes them from confirmed bookings")
    void testGetCustomerBookings_LazilyExpiresPastHold() throws Exception {
        Booking staleBooking = Booking.builder()
                .id(99L)
                .bookingRef("BKG-STALE-HOLD")
                .customerId(10L)
                .bookingDate(LocalDateTime.now().minusMinutes(20))
                .status("PENDING_PAYMENT")
                .build();

        when(bookingRepository.findByCustomerIdOrderByBookingDateDesc(10L))
                .thenReturn(new java.util.ArrayList<>(List.of(staleBooking)));

        mockMvc.perform(get("/api/bookings/customer/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(expiryService).expireBookingByRef("BKG-STALE-HOLD");
    }

    @Test
    @DisplayName("GET /api/bookings/customer/{customerId} - Returns only confirmed and cancelled bookings, excluding pending and expired attempts")
    void testGetCustomerBookings_ExcludesPendingAndExpired_ReturnsOnlyConfirmed() throws Exception {
        Booking pendingBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-PENDING")
                .customerId(10L)
                .status("PENDING_PAYMENT")
                .bookingDate(LocalDateTime.now().minusMinutes(5))
                .build();

        Booking expiredBooking = Booking.builder()
                .id(2L)
                .bookingRef("BKG-EXPIRED")
                .customerId(10L)
                .status("EXPIRED")
                .bookingDate(LocalDateTime.now().minusMinutes(30))
                .build();

        Booking confirmedBooking = Booking.builder()
                .id(3L)
                .bookingRef("BKG-CONFIRMED")
                .customerId(10L)
                .status("CONFIRMED")
                .bookingDate(LocalDateTime.now().minusHours(2))
                .build();

        Booking cancelledBooking = Booking.builder()
                .id(4L)
                .bookingRef("BKG-CANCELLED")
                .customerId(10L)
                .status("CANCELLED")
                .bookingDate(LocalDateTime.now().minusDays(1))
                .build();

        when(bookingRepository.findByCustomerIdOrderByBookingDateDesc(10L))
                .thenReturn(new java.util.ArrayList<>(List.of(pendingBooking, expiredBooking, confirmedBooking, cancelledBooking)));

        mockMvc.perform(get("/api/bookings/customer/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bookingRef").value("BKG-CONFIRMED"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[1].bookingRef").value("BKG-CANCELLED"))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"));
    }

    @Test
    @DisplayName("GET /api/bookings/customer/{customerId}?includeUnconfirmed=true - Returns all records including pending and expired")
    void testGetCustomerBookings_IncludeUnconfirmed_ReturnsAll() throws Exception {
        Booking pendingBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-PENDING")
                .customerId(10L)
                .status("PENDING_PAYMENT")
                .bookingDate(LocalDateTime.now().minusMinutes(5))
                .build();

        Booking confirmedBooking = Booking.builder()
                .id(2L)
                .bookingRef("BKG-CONFIRMED")
                .customerId(10L)
                .status("CONFIRMED")
                .bookingDate(LocalDateTime.now().minusHours(1))
                .build();

        when(bookingRepository.findByCustomerIdOrderByBookingDateDesc(10L))
                .thenReturn(new java.util.ArrayList<>(List.of(pendingBooking, confirmedBooking)));

        mockMvc.perform(get("/api/bookings/customer/10?includeUnconfirmed=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bookingRef").value("BKG-PENDING"))
                .andExpect(jsonPath("$[1].bookingRef").value("BKG-CONFIRMED"));
    }

    @Test
    @DisplayName("GET /api/bookings/customer/{customerId} - Returns empty array when traveller has no confirmed bookings")
    void testGetCustomerBookings_Empty_ReturnsEmptyArray() throws Exception {
        when(bookingRepository.findByCustomerIdOrderByBookingDateDesc(99L))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/bookings/customer/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ==================== Unit 2.3: Provider Bookings Ownership Tests ====================

    @Test
    @DisplayName("Unit 2.3: GET /api/bookings/provider/{id} - Unauthenticated request returns 401")
    void testGetProviderBookings_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/bookings/provider/888"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Authentication required")));
    }

    @Test
    @DisplayName("Unit 2.3: GET /api/bookings/provider/{id} - Invalid token returns 401")
    void testGetProviderBookings_InvalidToken_Returns401() throws Exception {
        when(securityService.validateToken("bad-token")).thenReturn(false);

        mockMvc.perform(get("/api/bookings/provider/888")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Invalid or expired")));
    }

    @Test
    @DisplayName("Unit 2.3: GET /api/bookings/provider/{id} - Customer role returns 403")
    void testGetProviderBookings_CustomerRole_Returns403() throws Exception {
        when(securityService.validateToken("cust-token")).thenReturn(true);
        when(securityService.extractRole("cust-token")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/bookings/provider/888")
                        .header("Authorization", "Bearer cust-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Provider or Admin role required")));
    }

    @Test
    @DisplayName("Unit 2.3: GET /api/bookings/provider/{id} - Cross-provider access returns 403")
    void testGetProviderBookings_CrossProviderAccess_Returns403() throws Exception {
        when(securityService.validateToken("op777-token")).thenReturn(true);
        when(securityService.extractRole("op777-token")).thenReturn("ROLE_BUS_OPERATOR");
        when(securityService.extractUserId("op777-token")).thenReturn(777L);

        mockMvc.perform(get("/api/bookings/provider/888")
                        .header("Authorization", "Bearer op777-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Cannot access bookings for another provider")));
    }

    @Test
    @DisplayName("Unit 2.3: GET /api/bookings/provider/{id} - Authorized Bus Operator returns 200 with bookings")
    void testGetProviderBookings_AuthorizedBusOperator_Returns200() throws Exception {
        when(securityService.validateToken("op888-token")).thenReturn(true);
        when(securityService.extractRole("op888-token")).thenReturn("ROLE_BUS_OPERATOR");
        when(securityService.extractUserId("op888-token")).thenReturn(888L);

        Booking b = Booking.builder()
                .id(1L)
                .bookingRef("BKG-OP888")
                .providerId(888L)
                .serviceType("BUS")
                .status("CONFIRMED")
                .build();
        when(bookingRepository.findByProviderIdOrderByBookingDateDesc(888L)).thenReturn(List.of(b));

        mockMvc.perform(get("/api/bookings/provider/888")
                        .header("Authorization", "Bearer op888-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingRef").value("BKG-OP888"))
                .andExpect(jsonPath("$[0].providerId").value(888));
    }

    @Test
    @DisplayName("Unit 2.3: GET /api/bookings/provider/{id} - Authorized Hotel Manager returns 200 with bookings")
    void testGetProviderBookings_AuthorizedHotelManager_Returns200() throws Exception {
        when(securityService.validateToken("mgr555-token")).thenReturn(true);
        when(securityService.extractRole("mgr555-token")).thenReturn("ROLE_HOTEL_MANAGER");
        when(securityService.extractUserId("mgr555-token")).thenReturn(555L);

        Booking b = Booking.builder()
                .id(2L)
                .bookingRef("BKG-HOTEL555")
                .providerId(555L)
                .serviceType("HOTEL")
                .status("CONFIRMED")
                .build();
        when(bookingRepository.findByProviderIdOrderByBookingDateDesc(555L)).thenReturn(List.of(b));

        mockMvc.perform(get("/api/bookings/provider/555")
                        .header("Authorization", "Bearer mgr555-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingRef").value("BKG-HOTEL555"))
                .andExpect(jsonPath("$[0].providerId").value(555));
    }

    @Test
    @DisplayName("Unit 2.3: GET /api/bookings/provider/{id} - Admin can access any provider bookings")
    void testGetProviderBookings_Admin_Returns200() throws Exception {
        when(securityService.validateToken("admin-token")).thenReturn(true);
        when(securityService.extractRole("admin-token")).thenReturn("ROLE_ADMIN");
        when(securityService.extractUserId("admin-token")).thenReturn(1L);

        Booking b = Booking.builder()
                .id(3L)
                .bookingRef("BKG-ADMIN-VIEW")
                .providerId(888L)
                .serviceType("BUS")
                .status("CONFIRMED")
                .build();
        when(bookingRepository.findByProviderIdOrderByBookingDateDesc(888L)).thenReturn(List.of(b));

        mockMvc.perform(get("/api/bookings/provider/888")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingRef").value("BKG-ADMIN-VIEW"));
    }

    // ==================== UNIT 3.2: SCHEDULE CANCELLATION TESTS ====================

    @Test
    @DisplayName("Unit 3.2: PUT /api/bookings/operator/schedules/{id}/cancel - Success returns 200 OK")
    void testCancelSchedule_Success_Returns200() throws Exception {
        ScheduleCancellationBatch batch = ScheduleCancellationBatch.builder()
                .id(1L)
                .scheduleId(10L)
                .operatorId(5L)
                .batchStatus("COMPLETED")
                .totalBookings(3)
                .refundsProcessed(2)
                .refundsFailed(0)
                .unpaidCancelled(1)
                .totalRefundAmount(1200.0)
                .build();

        when(coordinatorService.cancelSchedule(eq(10L), any())).thenReturn(batch);

        mockMvc.perform(put("/api/bookings/operator/schedules/10/cancel")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(10))
                .andExpect(jsonPath("$.batchStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.refundsProcessed").value(2))
                .andExpect(jsonPath("$.totalRefundAmount").value(1200.0));
    }

    @Test
    @DisplayName("Unit 3.2: PUT /api/bookings/operator/schedules/{id}/cancel - Partial failure returns 207 Multi-Status")
    void testCancelSchedule_PartialFailure_Returns207() throws Exception {
        ScheduleCancellationBatch batch = ScheduleCancellationBatch.builder()
                .id(1L)
                .scheduleId(10L)
                .operatorId(5L)
                .batchStatus("PARTIALLY_FAILED")
                .totalBookings(2)
                .refundsProcessed(1)
                .refundsFailed(1)
                .totalRefundAmount(600.0)
                .build();

        when(coordinatorService.cancelSchedule(eq(10L), any())).thenReturn(batch);

        mockMvc.perform(put("/api/bookings/operator/schedules/10/cancel")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.scheduleId").value(10))
                .andExpect(jsonPath("$.batchStatus").value("PARTIALLY_FAILED"))
                .andExpect(jsonPath("$.refundsFailed").value(1));
    }

    @Test
    @DisplayName("Unit 3.2: PUT /api/bookings/operator/schedules/{id}/cancel - Concurrency conflict returns 409 Conflict")
    void testCancelSchedule_Conflict_Returns409() throws Exception {
        when(coordinatorService.cancelSchedule(eq(10L), any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Cancellation already in progress for schedule 10"));

        mockMvc.perform(put("/api/bookings/operator/schedules/10/cancel")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Cancellation already in progress")));
    }

    @Test
    @DisplayName("Unit 3.2: PUT /api/bookings/operator/schedules/{id}/cancel - Cross-operator access returns 403 Forbidden")
    void testCancelSchedule_Forbidden_Returns403() throws Exception {
        when(coordinatorService.cancelSchedule(eq(10L), any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to cancel this schedule."));

        mockMvc.perform(put("/api/bookings/operator/schedules/10/cancel")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("permission")));
    }

    @Test
    @DisplayName("Unit 3.2: GET /api/bookings/operator/schedules/{id}/cancellation-status - Returns status 200 OK")
    void testGetCancellationStatus_Returns200() throws Exception {
        ScheduleCancellationBatch batch = ScheduleCancellationBatch.builder()
                .id(1L)
                .scheduleId(10L)
                .operatorId(5L)
                .batchStatus("COMPLETED")
                .totalBookings(3)
                .refundsProcessed(2)
                .build();

        when(coordinatorService.getCancellationStatus(eq(10L), any())).thenReturn(batch);

        mockMvc.perform(get("/api/bookings/operator/schedules/10/cancellation-status")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(10))
                .andExpect(jsonPath("$.batchStatus").value("COMPLETED"));
    }

    // ==========================================
    // WP-01: GET /api/bookings/admin/all Security Tests
    // ==========================================

    @Test
    @DisplayName("WP-01: GET /api/bookings/admin/all - Missing Token returns 401 Unauthorized")
    void getAllBookings_MissingToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/bookings/admin/all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authorization Bearer token is required"));
    }

    @Test
    @DisplayName("WP-01: GET /api/bookings/admin/all - Invalid Token returns 401 Unauthorized")
    void getAllBookings_InvalidToken_Returns401() throws Exception {
        when(securityService.validateToken("invalid.token")).thenReturn(false);

        mockMvc.perform(get("/api/bookings/admin/all")
                        .header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired authorization token"));
    }

    @Test
    @DisplayName("WP-01: GET /api/bookings/admin/all - As Customer returns 403 Forbidden")
    void getAllBookings_AsCustomer_Returns403() throws Exception {
        when(securityService.validateToken("customer.token")).thenReturn(true);
        when(securityService.extractRole("customer.token")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/bookings/admin/all")
                        .header("Authorization", "Bearer customer.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("WP-01: GET /api/bookings/admin/all - As Bus Operator returns 403 Forbidden")
    void getAllBookings_AsBusOperator_Returns403() throws Exception {
        when(securityService.validateToken("operator.token")).thenReturn(true);
        when(securityService.extractRole("operator.token")).thenReturn("ROLE_BUS_OPERATOR");

        mockMvc.perform(get("/api/bookings/admin/all")
                        .header("Authorization", "Bearer operator.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("WP-01: GET /api/bookings/admin/all - As Hotel Manager returns 403 Forbidden")
    void getAllBookings_AsHotelManager_Returns403() throws Exception {
        when(securityService.validateToken("manager.token")).thenReturn(true);
        when(securityService.extractRole("manager.token")).thenReturn("ROLE_HOTEL_MANAGER");

        mockMvc.perform(get("/api/bookings/admin/all")
                        .header("Authorization", "Bearer manager.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("WP-01: GET /api/bookings/admin/all - As Administrator returns 200 OK")
    void getAllBookings_AsAdmin_Returns200() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        Booking b = Booking.builder()
                .id(1L)
                .bookingRef("BKG-001")
                .customerId(101L)
                .customerName("Rahul V")
                .totalAmount(1200.0)
                .status("CONFIRMED")
                .build();
        when(bookingRepository.findAll()).thenReturn(List.of(b));

        mockMvc.perform(get("/api/bookings/admin/all")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingRef").value("BKG-001"))
                .andExpect(jsonPath("$[0].customerName").value("Rahul V"));
    }

    // ==========================================
    // WP-03: GET /api/bookings/admin/stats
    // ==========================================

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/stats - Without Token returns 401")
    void getBookingStats_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/bookings/admin/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authorization Bearer token is required"));
    }

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/stats - As Customer returns 403 Forbidden")
    void getBookingStats_AsCustomer_Returns403() throws Exception {
        when(securityService.validateToken("cust.token")).thenReturn(true);
        when(securityService.extractRole("cust.token")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/bookings/admin/stats")
                        .header("Authorization", "Bearer cust.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/stats - As Bus Operator returns 403 Forbidden")
    void getBookingStats_AsBusOperator_Returns403() throws Exception {
        when(securityService.validateToken("operator.token")).thenReturn(true);
        when(securityService.extractRole("operator.token")).thenReturn("ROLE_BUS_OPERATOR");

        mockMvc.perform(get("/api/bookings/admin/stats")
                        .header("Authorization", "Bearer operator.token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/stats - As Admin returns correct totals and percentages")
    void getBookingStats_AsAdmin_ReturnsCorrectMetrics() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        List<Object[]> mockResults = List.of(
                new Object[]{"BUS", 6407L},
                new Object[]{"HOTEL", 2909L}
        );
        when(bookingRepository.countConfirmedBookingsByServiceType()).thenReturn(mockResults);

        mockMvc.perform(get("/api/bookings/admin/stats")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(9316))
                .andExpect(jsonPath("$.serviceBreakdown.bus.count").value(6407))
                .andExpect(jsonPath("$.serviceBreakdown.bus.percentage").value(68.8))
                .andExpect(jsonPath("$.serviceBreakdown.hotel.count").value(2909))
                .andExpect(jsonPath("$.serviceBreakdown.hotel.percentage").value(31.2));
    }

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/stats - Zero bookings returns 0.0 percentages without divide-by-zero")
    void getBookingStats_ZeroBookings_ReturnsZero() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        when(bookingRepository.countConfirmedBookingsByServiceType()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/bookings/admin/stats")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(0))
                .andExpect(jsonPath("$.serviceBreakdown.bus.count").value(0))
                .andExpect(jsonPath("$.serviceBreakdown.bus.percentage").value(0.0))
                .andExpect(jsonPath("$.serviceBreakdown.hotel.count").value(0))
                .andExpect(jsonPath("$.serviceBreakdown.hotel.percentage").value(0.0));
    }

    // ==========================================
    // WP-03: GET /api/bookings/admin/trends
    // ==========================================

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/trends - Without Token returns 401")
    void getBookingTrends_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/bookings/admin/trends?days=7"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/trends - As Customer returns 403")
    void getBookingTrends_AsCustomer_Returns403() throws Exception {
        when(securityService.validateToken("cust.token")).thenReturn(true);
        when(securityService.extractRole("cust.token")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/bookings/admin/trends?days=7"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/trends - As Admin returns continuous chronological timeline")
    void getBookingTrends_AsAdmin_ReturnsContinuousTimeline() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        LocalDate today = LocalDate.now();
        List<Object[]> queryResults = List.of(
                new Object[]{java.sql.Date.valueOf(today.minusDays(1)), 25L},
                new Object[]{java.sql.Date.valueOf(today), 40L}
        );
        when(bookingRepository.countConfirmedBookingsByDateNative(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(queryResults);

        mockMvc.perform(get("/api/bookings/admin/trends?days=3")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$[0].bookingCount").value(0))
                .andExpect(jsonPath("$[1].date").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$[1].bookingCount").value(25))
                .andExpect(jsonPath("$[2].date").value(today.toString()))
                .andExpect(jsonPath("$[2].bookingCount").value(40));
    }

    // ==========================================
    // WP-03: GET /api/bookings/admin/recent
    // ==========================================

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/recent - Without Token returns 401")
    void getRecentBookings_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/bookings/admin/recent?limit=5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/recent - As Customer returns 403")
    void getRecentBookings_AsCustomer_Returns403() throws Exception {
        when(securityService.validateToken("cust.token")).thenReturn(true);
        when(securityService.extractRole("cust.token")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/bookings/admin/recent?limit=5")
                        .header("Authorization", "Bearer cust.token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/recent - As Admin returns recent confirmed bookings")
    void getRecentBookings_AsAdmin_ReturnsRecentBookings() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        Booking b1 = Booking.builder()
                .bookingRef("BKG-BUS-1001")
                .serviceType("BUS")
                .customerName("Rahul V")
                .totalAmount(1240.0)
                .status("CONFIRMED")
                .bookingDate(LocalDateTime.of(2026, 10, 23, 14, 20))
                .build();

        Booking b2 = Booking.builder()
                .bookingRef("BKG-HTL-2002")
                .serviceType("HOTEL")
                .customerName("Sneha M")
                .totalAmount(4500.0)
                .status("CONFIRMED")
                .bookingDate(LocalDateTime.of(2026, 10, 23, 15, 10))
                .build();

        PageImpl<Booking> pageResult = new PageImpl<>(List.of(b1, b2), PageRequest.of(0, 5), 2);
        when(bookingRepository.findRecentConfirmedBookings(any())).thenReturn(pageResult);

        mockMvc.perform(get("/api/bookings/admin/recent?limit=5")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bookingRef").value("BKG-BUS-1001"))
                .andExpect(jsonPath("$[0].serviceType").value("BUS"))
                .andExpect(jsonPath("$[0].customerName").value("Rahul V"))
                .andExpect(jsonPath("$[0].totalAmount").value(1240.0))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[1].bookingRef").value("BKG-HTL-2002"))
                .andExpect(jsonPath("$[1].serviceType").value("HOTEL"))
                .andExpect(jsonPath("$[1].customerName").value("Sneha M"));
    }

    @Test
    @DisplayName("WP-03: GET /api/bookings/admin/recent - Falls back to customer email when customerName is null")
    void getRecentBookings_CustomerNameFallback() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        Booking b = Booking.builder()
                .bookingRef("BKG-FALLBACK-1")
                .serviceType("BUS")
                .customerName(null)
                .customerEmail("guest@traveleasy.com")
                .totalAmount(950.0)
                .status("CONFIRMED")
                .bookingDate(LocalDateTime.now())
                .build();

        PageImpl<Booking> pageResult = new PageImpl<>(List.of(b), PageRequest.of(0, 5), 1);
        when(bookingRepository.findRecentConfirmedBookings(any())).thenReturn(pageResult);

        mockMvc.perform(get("/api/bookings/admin/recent?limit=5")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerName").value("guest@traveleasy.com"));
    }

    @Test
    @DisplayName("GET /api/bookings/hotel/{hotelId} - Enforces hotel ownership and enriches snapshot allocations")
    void getHotelBookings_OwnershipAndSnapshotEnrichment() throws Exception {
        mockMvc.perform(get("/api/bookings/hotel/10"))
                .andExpect(status().isUnauthorized());

        when(securityService.validateToken("mgr.token")).thenReturn(true);
        when(securityService.extractRole("mgr.token")).thenReturn("ROLE_HOTEL_MANAGER");
        when(securityService.extractUserId("mgr.token")).thenReturn(5L);
        when(internalTokenService.generateInternalToken()).thenReturn("booking-internal-jwt");

        // Cross-hotel manager denied (hotel owned by 99L)
        when(restTemplate.exchange(
                contains("/api/hotels/internal/10"),
                eq(HttpMethod.GET),
                any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("id", 10L, "managerId", 99L)));

        mockMvc.perform(get("/api/bookings/hotel/10")
                        .header("Authorization", "Bearer mgr.token"))
                .andExpect(status().isForbidden());

        // Owning manager allowed (hotel owned by 5L) + allocation enrichment
        when(restTemplate.exchange(
                contains("/api/hotels/internal/10"),
                eq(HttpMethod.GET),
                any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("id", 10L, "managerId", 5L)));

        Booking hotelBooking = Booking.builder()
                .id(77L)
                .bookingRef("HTL-77")
                .serviceType("HOTEL")
                .serviceId(10L)
                .providerId(5L)
                .customerName("Aditi Rao")
                .status("CONFIRMED")
                .totalAmount(9500.0)
                .checkInDate(LocalDate.of(2026, 11, 12))
                .checkOutDate(LocalDate.of(2026, 11, 14))
                .bookingDate(LocalDateTime.of(2026, 11, 10, 11, 0))
                .build();

        when(bookingRepository.findByServiceTypeAndServiceIdOrderByBookingDateDesc("HOTEL", 10L))
                .thenReturn(List.of(hotelBooking));

        com.traveleasy.booking.model.HotelAllocationSnapshot alloc1 =
                com.traveleasy.booking.model.HotelAllocationSnapshot.builder()
                        .roomId(101L)
                        .roomTypeName("Deluxe Suite")
                        .quantity(2)
                        .build();
        com.traveleasy.booking.model.HotelAllocationSnapshot alloc2 =
                com.traveleasy.booking.model.HotelAllocationSnapshot.builder()
                        .roomId(102L)
                        .roomTypeName("Family Villa")
                        .quantity(1)
                        .build();
        com.traveleasy.booking.model.HotelReservationSnapshot snap =
                com.traveleasy.booking.model.HotelReservationSnapshot.builder()
                        .bookingId(77L)
                        .hotelId(10L)
                        .allocations(List.of(alloc1, alloc2))
                        .build();
        when(snapshotRepository.findByBookingIdIn(List.of(77L))).thenReturn(List.of(snap));

        mockMvc.perform(get("/api/bookings/hotel/10")
                        .header("Authorization", "Bearer mgr.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingRef").value("HTL-77"))
                .andExpect(jsonPath("$[0].totalRoomsBooked").value(3))
                .andExpect(jsonPath("$[0].roomTypeSummary").value("Deluxe Suite (x2), Family Villa"))
                .andExpect(jsonPath("$[0].allocations.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/bookings/hotel/{hotelId}/refs - Rejects unauthenticated internal header bypass and serves valid internal token")
    void getHotelBookingRefs_StrictInternalAuth() throws Exception {
        when(securityService.verifyStrictInternalToken(any(), any(), any(), eq("payment-service"), eq("hotel-service")))
                .thenReturn(false)
                .thenReturn(true);

        mockMvc.perform(get("/api/bookings/hotel/10/refs")
                        .header("X-Internal-Service", "payment-service"))
                .andExpect(status().isUnauthorized());

        when(bookingRepository.findDistinctHotelBookingRefsByHotelId(10L))
                .thenReturn(List.of("HTL-101", "HTL-102"));

        mockMvc.perform(get("/api/bookings/hotel/10/refs")
                        .header("X-Internal-Token", "signed-payment-jwt")
                        .header("X-Internal-Service", "payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("HTL-101"))
                .andExpect(jsonPath("$[1]").value("HTL-102"));
    }
}
