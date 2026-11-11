package com.traveleasy.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.CancellationPolicyResult;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.HotelReservationSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

public class HotelCancellationPolicyStrictTest {

    private HotelCancellationPolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new HotelCancellationPolicyService(new ObjectMapper().findAndRegisterModules());
    }

    private Booking baseHotelBooking(double totalAmount, LocalDate checkIn, LocalDate checkOut) {
        return Booking.builder()
                .id(10L)
                .bookingRef("BKG-HTL-STRICT-01")
                .serviceType("HOTEL")
                .serviceId(25L)
                .customerId(100L)
                .totalAmount(totalAmount)
                .status("CONFIRMED")
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .build();
    }

    @Test
    @DisplayName("Policy zone is fixed to Asia/Kolkata (IST) and 14:00 check-in reference time")
    void testPolicyZoneAndReferenceTime() {
        assertEquals(ZoneId.of("Asia/Kolkata"), HotelCancellationPolicyService.POLICY_ZONE_ID);
        assertEquals(14, HotelCancellationPolicyService.STANDARD_CHECK_IN_TIME.getHour());
        assertEquals(0, HotelCancellationPolicyService.STANDARD_CHECK_IN_TIME.getMinute());
    }

    @Test
    @DisplayName("Exact Duration boundaries: 48h00m00s, 47h59m59s, 24h00m00s, 23h59m59s, 13:59:59, 14:00:00, 14:00:01")
    void testExactDurationBoundaries() {
        LocalDate checkIn = LocalDate.of(2026, 11, 20);
        LocalDate checkOut = LocalDate.of(2026, 11, 22);
        Booking booking = baseHotelBooking(4000.0, checkIn, checkOut);

        // Exactly 48h00m00s before 2026-11-20 14:00:00 -> FREE_CANCELLATION_48H (100%)
        CancellationPolicyResult at48h = policyService.evaluateStrictHotelCancellationPolicy(
                booking, null, LocalDateTime.of(2026, 11, 18, 14, 0, 0));
        assertEquals(HotelCancellationPolicyService.TIER_FREE_48H, at48h.getPolicyTier());
        assertEquals(100.0, at48h.getRefundPercentage());
        assertEquals(4000.0, at48h.getRefundAmount());
        assertEquals(0.0, at48h.getCancellationFee());
        assertTrue(at48h.isEligibleForRefund());
        assertEquals(LocalDateTime.of(2026, 11, 20, 14, 0, 0), at48h.getScheduledCheckInTime());

        // 47h59m59s -> PARTIAL_REFUND_50_24H_48H (50%)
        CancellationPolicyResult under48h = policyService.evaluateStrictHotelCancellationPolicy(
                booking, null, LocalDateTime.of(2026, 11, 18, 14, 0, 1));
        assertEquals(HotelCancellationPolicyService.TIER_PARTIAL_50, under48h.getPolicyTier());
        assertEquals(50.0, under48h.getRefundPercentage());
        assertEquals(2000.0, under48h.getRefundAmount());
        assertEquals(2000.0, under48h.getCancellationFee());
        assertTrue(under48h.isEligibleForRefund());

        // Exactly 24h00m00s -> PARTIAL_REFUND_50_24H_48H (50%)
        CancellationPolicyResult at24h = policyService.evaluateStrictHotelCancellationPolicy(
                booking, null, LocalDateTime.of(2026, 11, 19, 14, 0, 0));
        assertEquals(HotelCancellationPolicyService.TIER_PARTIAL_50, at24h.getPolicyTier());
        assertEquals(50.0, at24h.getRefundPercentage());
        assertTrue(at24h.isEligibleForRefund());

        // 23h59m59s -> NON_REFUNDABLE_LESS_THAN_24H (0%)
        CancellationPolicyResult under24h = policyService.evaluateStrictHotelCancellationPolicy(
                booking, null, LocalDateTime.of(2026, 11, 19, 14, 0, 1));
        assertEquals(HotelCancellationPolicyService.TIER_NON_REFUNDABLE_24H, under24h.getPolicyTier());
        assertEquals(0.0, under24h.getRefundPercentage());
        assertEquals(0.0, under24h.getRefundAmount());
        assertEquals(4000.0, under24h.getCancellationFee());
        assertFalse(under24h.isEligibleForRefund());

        // 13:59:59 on check-in day -> NON_REFUNDABLE_LESS_THAN_24H (0%)
        CancellationPolicyResult justBeforeCheckIn = policyService.evaluateStrictHotelCancellationPolicy(
                booking, null, LocalDateTime.of(2026, 11, 20, 13, 59, 59));
        assertEquals(HotelCancellationPolicyService.TIER_NON_REFUNDABLE_24H, justBeforeCheckIn.getPolicyTier());
        assertEquals(0.0, justBeforeCheckIn.getRefundAmount());
        assertFalse(justBeforeCheckIn.isEligibleForRefund());

        // 14:00:00 on check-in day -> PAST_CHECKIN_NON_REFUNDABLE (0%)
        CancellationPolicyResult atCheckIn = policyService.evaluateStrictHotelCancellationPolicy(
                booking, null, LocalDateTime.of(2026, 11, 20, 14, 0, 0));
        assertEquals(HotelCancellationPolicyService.TIER_PAST_CHECKIN, atCheckIn.getPolicyTier());
        assertEquals(0.0, atCheckIn.getRefundPercentage());
        assertEquals(0.0, atCheckIn.getRefundAmount());
        assertEquals(4000.0, atCheckIn.getCancellationFee());
        assertFalse(atCheckIn.isEligibleForRefund());

        // 14:00:01 on check-in day -> PAST_CHECKIN_NON_REFUNDABLE (0%)
        CancellationPolicyResult pastCheckIn = policyService.evaluateStrictHotelCancellationPolicy(
                booking, null, LocalDateTime.of(2026, 11, 20, 14, 0, 1));
        assertEquals(HotelCancellationPolicyService.TIER_PAST_CHECKIN, pastCheckIn.getPolicyTier());
        assertEquals(0.0, pastCheckIn.getRefundAmount());
        assertFalse(pastCheckIn.isEligibleForRefund());
    }

    @Test
    @DisplayName("Odd-paise 50% BigDecimal rounding preserves refundAmount + cancellationFee == totalAmount")
    void testOddAmountBigDecimalInvariant() {
        LocalDate checkIn = LocalDate.of(2026, 11, 20);
        LocalDate checkOut = LocalDate.of(2026, 11, 21);
        LocalDateTime thirtyHoursBefore = LocalDateTime.of(2026, 11, 19, 8, 0, 0);

        Booking odd99 = baseHotelBooking(999.99, checkIn, checkOut);
        CancellationPolicyResult r1 = policyService.evaluateStrictHotelCancellationPolicy(odd99, null, thirtyHoursBefore);
        assertEquals(500.00, r1.getRefundAmount(), 0.0001);
        assertEquals(499.99, r1.getCancellationFee(), 0.0001);
        assertEquals(
                BigDecimal.valueOf(999.99),
                BigDecimal.valueOf(r1.getRefundAmount()).add(BigDecimal.valueOf(r1.getCancellationFee())));

        Booking odd01 = baseHotelBooking(100.01, checkIn, checkOut);
        CancellationPolicyResult r2 = policyService.evaluateStrictHotelCancellationPolicy(odd01, null, thirtyHoursBefore);
        assertEquals(50.01, r2.getRefundAmount(), 0.0001);
        assertEquals(50.00, r2.getCancellationFee(), 0.0001);
        assertEquals(
                BigDecimal.valueOf(100.01),
                BigDecimal.valueOf(r2.getRefundAmount()).add(BigDecimal.valueOf(r2.getCancellationFee())));
    }

    @Test
    @DisplayName("Snapshot check-in and check-out dates are used as authoritative fallback when Booking columns are null")
    void testSnapshotDateFallback() {
        Booking bookingWithoutDates = Booking.builder()
                .id(11L)
                .bookingRef("BKG-HTL-SNAP-FALLBACK")
                .serviceType("HOTEL")
                .serviceId(30L)
                .customerId(101L)
                .totalAmount(3000.0)
                .status("CONFIRMED")
                .build();

        HotelReservationSnapshot snapshot = HotelReservationSnapshot.builder()
                .bookingId(11L)
                .bookingRef("BKG-HTL-SNAP-FALLBACK")
                .hotelId(30L)
                .checkInDate(LocalDate.of(2026, 11, 20))
                .checkOutDate(LocalDate.of(2026, 11, 22))
                .status("HELD")
                .build();

        CancellationPolicyResult result = policyService.evaluateStrictHotelCancellationPolicy(
                bookingWithoutDates, snapshot, LocalDateTime.of(2026, 11, 17, 10, 0, 0));

        assertEquals(HotelCancellationPolicyService.TIER_FREE_48H, result.getPolicyTier());
        assertEquals(3000.0, result.getRefundAmount());
        assertEquals(LocalDateTime.of(2026, 11, 20, 14, 0, 0), result.getScheduledCheckInTime());
    }

    @Test
    @DisplayName("Fails closed with HTTP 400 when check-in date is missing, stay window invalid, or totalAmount invalid")
    void testFailClosedValidation() {
        // Null booking
        ResponseStatusException nullEx = assertThrows(ResponseStatusException.class,
                () -> policyService.evaluateStrictHotelCancellationPolicy(null, null, LocalDateTime.now()));
        assertEquals(HttpStatus.BAD_REQUEST, nullEx.getStatusCode());

        // Missing check-in date on both Booking and Snapshot
        Booking missingCheckIn = baseHotelBooking(2000.0, null, null);
        ResponseStatusException noCheckInEx = assertThrows(ResponseStatusException.class,
                () -> policyService.evaluateStrictHotelCancellationPolicy(missingCheckIn, null, LocalDateTime.now()));
        assertEquals(HttpStatus.BAD_REQUEST, noCheckInEx.getStatusCode());

        // checkOut == checkIn
        LocalDate d = LocalDate.of(2026, 11, 20);
        Booking equalDates = baseHotelBooking(2000.0, d, d);
        ResponseStatusException equalEx = assertThrows(ResponseStatusException.class,
                () -> policyService.evaluateStrictHotelCancellationPolicy(equalDates, null, LocalDateTime.of(2026, 11, 15, 10, 0)));
        assertEquals(HttpStatus.BAD_REQUEST, equalEx.getStatusCode());

        // checkOut < checkIn
        Booking reversedDates = baseHotelBooking(2000.0, d, d.minusDays(1));
        ResponseStatusException reversedEx = assertThrows(ResponseStatusException.class,
                () -> policyService.evaluateStrictHotelCancellationPolicy(reversedDates, null, LocalDateTime.of(2026, 11, 15, 10, 0)));
        assertEquals(HttpStatus.BAD_REQUEST, reversedEx.getStatusCode());

        // Negative or null totalAmount
        Booking negativeTotal = baseHotelBooking(-50.0, d, d.plusDays(1));
        ResponseStatusException negEx = assertThrows(ResponseStatusException.class,
                () -> policyService.evaluateStrictHotelCancellationPolicy(negativeTotal, null, LocalDateTime.of(2026, 11, 15, 10, 0)));
        assertEquals(HttpStatus.BAD_REQUEST, negEx.getStatusCode());

        Booking nullTotal = Booking.builder()
                .id(12L)
                .bookingRef("BKG-NULL-AMT")
                .serviceType("HOTEL")
                .totalAmount(null)
                .checkInDate(d)
                .checkOutDate(d.plusDays(1))
                .build();
        ResponseStatusException nullAmtEx = assertThrows(ResponseStatusException.class,
                () -> policyService.evaluateStrictHotelCancellationPolicy(nullTotal, null, LocalDateTime.of(2026, 11, 15, 10, 0)));
        assertEquals(HttpStatus.BAD_REQUEST, nullAmtEx.getStatusCode());
    }
}
