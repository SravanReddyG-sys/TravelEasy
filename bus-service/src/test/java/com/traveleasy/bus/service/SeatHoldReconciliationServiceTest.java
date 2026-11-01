package com.traveleasy.bus.service;

import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.Seat;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
public class SeatHoldReconciliationServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private BusScheduleRepository scheduleRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private SeatSegmentReservationService seatSegmentReservationService;

    @InjectMocks
    private SeatHoldReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reconciliationService, "paymentServiceUrl", "http://localhost:8085");
        ReflectionTestUtils.setField(reconciliationService, "bookingServiceUrl", "http://localhost:8084");
    }

    @Test
    @DisplayName("Expired hold without payment or booking is released to AVAILABLE and inventory restored")
    void testReconcileExpiredHolds_ReleaseUnpaidSeat() {
        Seat expiredSeat = Seat.builder()
                .id(101L)
                .scheduleId(5L)
                .seatNumber("L1")
                .status("LOCKED")
                .holdBookingRef("BK-EXPIRED-1")
                .holdExpiresAt(LocalDateTime.now().minusMinutes(5))
                .build();

        BusSchedule schedule = BusSchedule.builder()
                .id(5L)
                .availableSeats(20)
                .build();

        when(seatRepository.findByStatusInAndHoldExpiresAtBefore(eq(List.of("LOCKED", "BOOKED")), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredSeat));

        // Mock payment not found or failed
        when(restTemplate.getForEntity(contains("/api/payments/booking/BK-EXPIRED-1"), eq(Map.class)))
                .thenThrow(new RuntimeException("Payment not found"));

        // Mock booking not confirmed
        when(restTemplate.getForEntity(contains("/api/bookings/ref/BK-EXPIRED-1"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "PENDING"), HttpStatus.OK));

        when(scheduleRepository.findById(5L)).thenReturn(Optional.of(schedule));

        reconciliationService.reconcileExpiredHolds();

        assertEquals("AVAILABLE", expiredSeat.getStatus());
        assertNull(expiredSeat.getHoldExpiresAt());
        assertNull(expiredSeat.getHoldBookingRef());
        verify(seatRepository).save(expiredSeat);

        assertEquals(21, schedule.getAvailableSeats());
        verify(scheduleRepository).save(schedule);
    }

    @Test
    @DisplayName("Expired hold with confirmed payment in Payment Service is transitioned to BOOKED and preserved")
    void testReconcileExpiredHolds_PreservePaidSeat() {
        Seat expiredSeat = Seat.builder()
                .id(102L)
                .scheduleId(5L)
                .seatNumber("L2")
                .status("LOCKED")
                .holdBookingRef("BK-PAID-1")
                .holdExpiresAt(LocalDateTime.now().minusMinutes(2))
                .build();

        when(seatRepository.findByStatusInAndHoldExpiresAtBefore(eq(List.of("LOCKED", "BOOKED")), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredSeat));

        // Mock payment service returning SUCCESS
        when(restTemplate.getForEntity(contains("/api/payments/booking/BK-PAID-1"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "SUCCESS"), HttpStatus.OK));

        reconciliationService.reconcileExpiredHolds();

        assertEquals("BOOKED", expiredSeat.getStatus());
        assertNull(expiredSeat.getHoldExpiresAt());
        assertEquals("BK-PAID-1", expiredSeat.getHoldBookingRef());
        verify(seatRepository).save(expiredSeat);

        // Schedule available seats must NOT be incremented
        verify(scheduleRepository, never()).findById(anyLong());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Expired hold with CONFIRMED booking in Booking Service is preserved as BOOKED")
    void testReconcileExpiredHolds_PreserveConfirmedBooking() {
        Seat expiredSeat = Seat.builder()
                .id(103L)
                .scheduleId(6L)
                .seatNumber("U1")
                .status("LOCKED")
                .holdBookingRef("BK-CONFIRMED-BOOKING")
                .holdExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(seatRepository.findByStatusInAndHoldExpiresAtBefore(eq(List.of("LOCKED", "BOOKED")), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredSeat));

        // Mock payment service throwing 404
        when(restTemplate.getForEntity(contains("/api/payments/booking/BK-CONFIRMED-BOOKING"), eq(Map.class)))
                .thenThrow(new RuntimeException("Not found in payment service"));

        // Mock booking service returning CONFIRMED
        when(restTemplate.getForEntity(contains("/api/bookings/ref/BK-CONFIRMED-BOOKING"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "CONFIRMED"), HttpStatus.OK));

        reconciliationService.reconcileExpiredHolds();

        assertEquals("BOOKED", expiredSeat.getStatus());
        assertNull(expiredSeat.getHoldExpiresAt());
        verify(seatRepository).save(expiredSeat);

        verify(scheduleRepository, never()).findById(anyLong());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("No expired seats found does nothing")
    void testReconcileExpiredHolds_NoExpiredSeats() {
        when(seatRepository.findByStatusInAndHoldExpiresAtBefore(eq(List.of("LOCKED", "BOOKED")), any(LocalDateTime.class)))
                .thenReturn(List.of());

        reconciliationService.reconcileExpiredHolds();

        verify(seatRepository, never()).save(any());
        verify(scheduleRepository, never()).save(any());
    }
}
