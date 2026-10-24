package com.traveleasy.booking.service;

import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingExpiryReconciliationServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private BookingExpiryReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reconciliationService, "busServiceUrl", "http://localhost:8082");
        ReflectionTestUtils.setField(reconciliationService, "hotelServiceUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(reconciliationService, "paymentServiceUrl", "http://localhost:8085");
        reconciliationService.setRestTemplate(restTemplate);
    }

    @Test
    @DisplayName("Unpaid booking past 15-minute threshold is expired and seats are released")
    void testReconciliation_UnpaidBooking_ExpiresAndReleasesSeats() {
        Booking staleBooking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-STALE-1")
                .customerId(10L)
                .serviceType("BUS")
                .serviceId(3L)
                .detailsJson("Bus: Intercity | Seats: 2A, 2B")
                .status("PENDING_PAYMENT")
                .bookingDate(LocalDateTime.now().minusMinutes(20))
                .build();

        when(bookingRepository.findByStatusInAndBookingDateBefore(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(staleBooking));

        // Payment check returns not found / unpaid
        when(restTemplate.getForEntity(contains("/api/payments/booking/BKG-STALE-1"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "FAILED"), HttpStatus.OK));

        // Seat release succeeds
        when(restTemplate.exchange(contains("/api/buses/schedules/3/release-seats"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("message", "Seats released"), HttpStatus.OK));

        reconciliationService.reconcileExpiredBookings();

        assertEquals("EXPIRED", staleBooking.getStatus());
        verify(bookingRepository).save(staleBooking);
        verify(restTemplate).exchange(contains("/api/buses/schedules/3/release-seats"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("Paid booking past threshold is confirmed when seats are still available")
    void testReconciliation_PaidBooking_Confirmed() {
        Booking paidBooking = Booking.builder()
                .id(2L)
                .bookingRef("BKG-PAID-RECOVER")
                .customerId(10L)
                .serviceType("BUS")
                .serviceId(3L)
                .detailsJson("Bus: Intercity | Seats: 1A")
                .status("PENDING_PAYMENT")
                .bookingDate(LocalDateTime.now().minusMinutes(16))
                .build();

        when(bookingRepository.findByStatusInAndBookingDateBefore(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(paidBooking));

        // Payment captured
        when(restTemplate.getForEntity(contains("/api/payments/booking/BKG-PAID-RECOVER"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "SUCCESS"), HttpStatus.OK));

        // Seat confirmation succeeds
        when(restTemplate.exchange(contains("/api/buses/schedules/3/confirm-seats"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("message", "Seats confirmed"), HttpStatus.OK));

        reconciliationService.reconcileExpiredBookings();

        assertEquals("CONFIRMED", paidBooking.getStatus());
        verify(bookingRepository).save(paidBooking);
    }

    @Test
    @DisplayName("Late payment with seats already taken triggers refund and marks booking EXPIRED")
    void testReconciliation_LatePayment_SeatsConflict_RefundTriggered() {
        Booking raceBooking = Booking.builder()
                .id(3L)
                .bookingRef("BKG-RACE-CONFLICT")
                .customerId(10L)
                .serviceType("BUS")
                .serviceId(3L)
                .totalAmount(600.0)
                .detailsJson("Bus: Intercity | Seats: 1B")
                .status("PENDING_PAYMENT")
                .bookingDate(LocalDateTime.now().minusMinutes(18))
                .build();

        when(bookingRepository.findByStatusInAndBookingDateBefore(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(raceBooking));

        // Payment captured
        when(restTemplate.getForEntity(contains("/api/payments/booking/BKG-RACE-CONFLICT"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "SUCCESS"), HttpStatus.OK));

        // Seat confirmation FAILS because another traveller booked 1B after the hold expired
        when(restTemplate.exchange(contains("/api/buses/schedules/3/confirm-seats"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("message", "Conflict"), HttpStatus.CONFLICT));

        // Refund mock
        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-RACE-CONFLICT"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("refundId", "rfnd_AUTO_456", "status", "SUCCESS"), HttpStatus.OK));

        reconciliationService.reconcileExpiredBookings();

        assertEquals("EXPIRED", raceBooking.getStatus());
        verify(bookingRepository).save(raceBooking);
        verify(restTemplate).exchange(contains("/api/payments/refund/booking/BKG-RACE-CONFLICT"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("expireBookingByRef returns false and preserves CONFIRMED bookings")
    void testExpireBookingByRef_AlreadyConfirmed_ReturnsFalse() {
        Booking confirmed = Booking.builder()
                .id(4L)
                .bookingRef("BKG-ALREADY-CONFIRMED")
                .status("CONFIRMED")
                .build();

        when(bookingRepository.findByBookingRef("BKG-ALREADY-CONFIRMED")).thenReturn(Optional.of(confirmed));

        boolean result = reconciliationService.expireBookingByRef("BKG-ALREADY-CONFIRMED");

        assertFalse(result);
        assertEquals("CONFIRMED", confirmed.getStatus());
        verify(bookingRepository, never()).save(any(Booking.class));
    }
}
