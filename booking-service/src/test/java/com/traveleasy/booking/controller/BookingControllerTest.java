package com.traveleasy.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.service.BookingExpiryReconciliationService;
import com.traveleasy.booking.service.BookingSecurityService;
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
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        bookingController.setRestTemplate(restTemplate);
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
                .andExpect(jsonPath("$.releasedSeats[0]").value("4A"))
                .andExpect(jsonPath("$.releasedSeats[1]").value("4B"))
                .andExpect(jsonPath("$.refund.refundId").value("rfnd_TEST999"));

        verify(restTemplate).exchange(contains("/api/buses/schedules/2/release-seats"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class));
        verify(restTemplate).exchange(contains("/api/payments/refund/booking/BKG-CANCEL-ME"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("PUT /api/bookings/{id}/cancel - Rejects cancellation if already CANCELLED")
    void testCancelBooking_AlreadyCancelled() throws Exception {
        Booking cancelledBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-ALREADY-CANCELLED")
                .customerId(10L)
                .status("CANCELLED")
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(cancelledBooking));

        mockMvc.perform(put("/api/bookings/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Booking is already cancelled"));

        verify(restTemplate, never()).exchange(contains("release-seats"), any(), any(), eq(Map.class));
        verify(restTemplate, never()).exchange(contains("refund"), any(), any(), eq(Map.class));
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
}
