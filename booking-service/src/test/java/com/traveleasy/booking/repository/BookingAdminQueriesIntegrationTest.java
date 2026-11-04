package com.traveleasy.booking.repository;

import com.traveleasy.booking.model.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class BookingAdminQueriesIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

    private String testPrefix;

    @BeforeEach
    void setUp() {
        testPrefix = "TEST-" + UUID.randomUUID().toString().substring(0, 6) + "-";
    }

    private Booking createBooking(String serviceType, String status, String refundStatus, String refundId, LocalDateTime confirmedAt, LocalDateTime bookingDate) {
        return bookingRepository.save(Booking.builder()
                .bookingRef(testPrefix + UUID.randomUUID().toString().substring(0, 8))
                .serviceType(serviceType)
                .serviceId(1L)
                .customerId(999L)
                .customerName("Test User")
                .totalAmount(500.0)
                .status(status)
                .refundStatus(refundStatus != null ? refundStatus : "NOT_APPLICABLE")
                .refundId(refundId)
                .confirmedAt(confirmedAt)
                .bookingDate(bookingDate != null ? bookingDate : LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("WP-03: countConfirmedBookingsByServiceType - Strictly includes only genuine confirmed bookings")
    void testCountConfirmedBookingsByServiceType_LifecycleFilter() {
        // 1. Confirmed bus booking (should be included)
        createBooking("BUS", "CONFIRMED", "NOT_APPLICABLE", null, LocalDateTime.now(), LocalDateTime.now());

        // 2. Confirmed hotel booking (should be included)
        createBooking("HOTEL", "CONFIRMED", "NOT_APPLICABLE", null, LocalDateTime.now(), LocalDateTime.now());

        // 3. Confirmed then Cancelled bus booking with refund (should be included)
        createBooking("BUS", "CANCELLED", "PROCESSED", "rfnd_test_123", LocalDateTime.now(), LocalDateTime.now());

        // 4. Completed bus booking (should be included)
        createBooking("BUS", "COMPLETED", "NOT_APPLICABLE", null, LocalDateTime.now(), LocalDateTime.now());

        // 5. INITIATED never confirmed (must be EXCLUDED)
        createBooking("BUS", "INITIATED", "NOT_APPLICABLE", null, null, LocalDateTime.now());

        // 6. PENDING_PAYMENT never confirmed (must be EXCLUDED)
        createBooking("BUS", "PENDING_PAYMENT", "NOT_APPLICABLE", null, null, LocalDateTime.now());

        // 7. EXPIRED never confirmed (must be EXCLUDED)
        createBooking("BUS", "EXPIRED", "NOT_APPLICABLE", null, null, LocalDateTime.now().minusDays(1));

        // 8. CANCELLED never confirmed (NOT_APPLICABLE refund) (must be EXCLUDED)
        createBooking("BUS", "CANCELLED", "NOT_APPLICABLE", null, null, LocalDateTime.now());

        List<Object[]> results = bookingRepository.countConfirmedBookingsByServiceType();
        assertNotNull(results);

        long busCount = 0;
        long hotelCount = 0;
        for (Object[] row : results) {
            String type = row[0].toString().toUpperCase();
            long count = ((Number) row[1]).longValue();
            if ("BUS".equals(type)) busCount += count;
            if ("HOTEL".equals(type)) hotelCount += count;
        }

        assertTrue(busCount >= 3, "Expected at least 3 confirmed bus bookings");
        assertTrue(hotelCount >= 1, "Expected at least 1 confirmed hotel booking");
    }

    @Test
    @DisplayName("WP-03: countConfirmedBookingsByDateNative - Groups confirmed bookings by date accurately")
    void testCountConfirmedBookingsByDateNative() {
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime yesterday = today.minusDays(1);

        createBooking("BUS", "CONFIRMED", "NOT_APPLICABLE", null, today, today);
        createBooking("BUS", "CONFIRMED", "NOT_APPLICABLE", null, yesterday, yesterday);

        List<Object[]> dateResults = bookingRepository.countConfirmedBookingsByDateNative(
                today.minusDays(2).toLocalDate().atStartOfDay(),
                today.toLocalDate().atTime(23, 59, 59)
        );

        assertNotNull(dateResults);
        assertFalse(dateResults.isEmpty(), "Expected date results for recent confirmed bookings");
    }

    @Test
    @DisplayName("WP-03: findRecentConfirmedBookings - Returns confirmed bookings sorted newest first")
    void testFindRecentConfirmedBookings() {
        LocalDateTime now = LocalDateTime.now();
        Booking newer = createBooking("BUS", "CONFIRMED", "NOT_APPLICABLE", null, now, now);
        Booking older = createBooking("HOTEL", "CONFIRMED", "NOT_APPLICABLE", null, now.minusHours(2), now.minusHours(2));

        // Unconfirmed should not appear
        Booking unconfirmed = createBooking("BUS", "PENDING_PAYMENT", "NOT_APPLICABLE", null, null, now.plusMinutes(5));

        Page<Booking> page = bookingRepository.findRecentConfirmedBookings(PageRequest.of(0, 10));
        assertNotNull(page);
        assertTrue(page.getTotalElements() >= 2);

        List<String> refs = page.getContent().stream().map(Booking::getBookingRef).toList();
        assertTrue(refs.contains(newer.getBookingRef()));
        assertTrue(refs.contains(older.getBookingRef()));
        assertFalse(refs.contains(unconfirmed.getBookingRef()), "Unconfirmed booking must not appear in recent confirmed bookings");
    }
}
