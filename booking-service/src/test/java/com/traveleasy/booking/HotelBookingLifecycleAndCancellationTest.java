package com.traveleasy.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.BookingLifecycleOperationResponse;
import com.traveleasy.booking.dto.CancellationPolicyResult;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.service.BookingInternalTokenService;
import com.traveleasy.booking.service.BookingStateTransitionService;
import com.traveleasy.booking.service.HotelCancellationPolicyService;
import com.traveleasy.booking.service.HotelGuestLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HotelBookingLifecycleAndCancellationTest {

    private HotelCancellationPolicyService cancellationPolicyService;
    private HotelGuestLifecycleService guestLifecycleService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingStateTransitionService stateTransitionService;

    @Mock
    private BookingInternalTokenService internalTokenService;

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        cancellationPolicyService = new HotelCancellationPolicyService(objectMapper);

        guestLifecycleService = new HotelGuestLifecycleService(
                bookingRepository,
                stateTransitionService,
                internalTokenService
        );
        guestLifecycleService.setRestTemplate(restTemplate);
    }

    // =========================================================================
    // 1. Central Platform Cancellation Policy Evaluation (BD-04)
    // =========================================================================

    @Test
    @DisplayName("BD-04: Cancellation >= 48 hours prior to check-in yields 100% refund and 0 fee")
    void testCancellationPolicy_MoreThan48Hours_FreeCancellation() {
        LocalDate checkIn = LocalDate.now().plusDays(5);
        Booking booking = Booking.builder()
                .bookingRef("BKG-HTL-FREE")
                .serviceType("HOTEL")
                .totalAmount(1000.0)
                .checkInDate(checkIn)
                .checkOutDate(checkIn.plusDays(2))
                .build();

        LocalDateTime evalTime = LocalDateTime.of(checkIn.minusDays(3), HotelCancellationPolicyService.STANDARD_CHECK_IN_TIME);
        CancellationPolicyResult result = cancellationPolicyService.evaluateCancellationPolicy(booking, evalTime);

        assertNotNull(result);
        assertEquals(HotelCancellationPolicyService.TIER_FREE_48H, result.getPolicyTier());
        assertEquals(100.0, result.getRefundPercentage());
        assertEquals(1000.0, result.getRefundAmount());
        assertEquals(0.0, result.getCancellationFee());
        assertTrue(result.isEligibleForRefund());
    }

    @Test
    @DisplayName("BD-04: Cancellation between 24 and 48 hours yields 50% refund and 50% fee")
    void testCancellationPolicy_Between24And48Hours_PartialRefund() {
        LocalDate checkIn = LocalDate.now().plusDays(2);
        Booking booking = Booking.builder()
                .bookingRef("BKG-HTL-PARTIAL")
                .serviceType("HOTEL")
                .totalAmount(1200.0)
                .checkInDate(checkIn)
                .checkOutDate(checkIn.plusDays(2))
                .build();

        // 30 hours before check-in (14:00)
        LocalDateTime checkInTime = LocalDateTime.of(checkIn, HotelCancellationPolicyService.STANDARD_CHECK_IN_TIME);
        LocalDateTime evalTime = checkInTime.minusHours(30);

        CancellationPolicyResult result = cancellationPolicyService.evaluateCancellationPolicy(booking, evalTime);

        assertNotNull(result);
        assertEquals(HotelCancellationPolicyService.TIER_PARTIAL_50, result.getPolicyTier());
        assertEquals(50.0, result.getRefundPercentage());
        assertEquals(600.0, result.getRefundAmount());
        assertEquals(600.0, result.getCancellationFee());
        assertTrue(result.isEligibleForRefund());
    }

    @Test
    @DisplayName("BD-04: Cancellation < 24 hours prior to check-in yields 0% refund and 100% fee")
    void testCancellationPolicy_LessThan24Hours_NonRefundable() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        Booking booking = Booking.builder()
                .bookingRef("BKG-HTL-LATE")
                .serviceType("HOTEL")
                .totalAmount(800.0)
                .checkInDate(checkIn)
                .checkOutDate(checkIn.plusDays(2))
                .build();

        // 12 hours before check-in (14:00)
        LocalDateTime checkInTime = LocalDateTime.of(checkIn, HotelCancellationPolicyService.STANDARD_CHECK_IN_TIME);
        LocalDateTime evalTime = checkInTime.minusHours(12);

        CancellationPolicyResult result = cancellationPolicyService.evaluateCancellationPolicy(booking, evalTime);

        assertNotNull(result);
        assertEquals(HotelCancellationPolicyService.TIER_NON_REFUNDABLE_24H, result.getPolicyTier());
        assertEquals(0.0, result.getRefundPercentage());
        assertEquals(0.0, result.getRefundAmount());
        assertEquals(800.0, result.getCancellationFee());
        assertFalse(result.isEligibleForRefund());
    }

    @Test
    @DisplayName("BD-04: Cancellation after scheduled check-in time yields PAST_CHECKIN_NON_REFUNDABLE")
    void testCancellationPolicy_AfterCheckInTime_NonRefundable() {
        LocalDate checkIn = LocalDate.now().minusDays(1);
        Booking booking = Booking.builder()
                .bookingRef("BKG-HTL-PAST")
                .serviceType("HOTEL")
                .totalAmount(1500.0)
                .checkInDate(checkIn)
                .checkOutDate(checkIn.plusDays(3))
                .build();

        LocalDateTime evalTime = LocalDateTime.now();
        CancellationPolicyResult result = cancellationPolicyService.evaluateCancellationPolicy(booking, evalTime);

        assertNotNull(result);
        assertEquals(HotelCancellationPolicyService.TIER_PAST_CHECKIN, result.getPolicyTier());
        assertEquals(0.0, result.getRefundPercentage());
        assertEquals(0.0, result.getRefundAmount());
        assertEquals(1500.0, result.getCancellationFee());
        assertFalse(result.isEligibleForRefund());
    }

    @Test
    @DisplayName("BD-07: Cancellation snapshot serialization preserves full audit metadata")
    void testCancellationSnapshotSerialization() {
        CancellationPolicyResult result = CancellationPolicyResult.builder()
                .policyTier(HotelCancellationPolicyService.TIER_PARTIAL_50)
                .refundPercentage(50.0)
                .refundAmount(450.0)
                .cancellationFee(450.0)
                .description("Standard 50% refund")
                .hoursUntilCheckIn(32)
                .eligibleForRefund(true)
                .build();

        String snapshotJson = cancellationPolicyService.buildPolicySnapshotJson(result);
        assertNotNull(snapshotJson);
        assertTrue(snapshotJson.contains("PARTIAL_REFUND_50_24H_48H"));
        assertTrue(snapshotJson.contains("450.0"));
    }

    // =========================================================================
    // 2. Operational Guest Lifecycle - Check-in (BD-09)
    // =========================================================================

    @Test
    @DisplayName("BD-09: Check-in succeeds for confirmed reservation arriving today")
    void testCheckInGuest_Success() {
        LocalDate today = LocalDate.now();
        Booking booking = Booking.builder()
                .bookingRef("BKG-CHECKIN-1")
                .serviceType("HOTEL")
                .providerId(5L)
                .status("CONFIRMED")
                .checkInDate(today)
                .checkOutDate(today.plusDays(2))
                .customerName("John Doe")
                .build();

        when(bookingRepository.findByBookingRef("BKG-CHECKIN-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingLifecycleOperationResponse response = guestLifecycleService.checkInGuest("BKG-CHECKIN-1", 5L, "ROLE_HOTEL_MANAGER", false);

        assertNotNull(response);
        assertEquals("CHECKED_IN", response.getNewStatus());
        assertEquals("CONFIRMED", response.getPreviousStatus());
        assertNotNull(booking.getCheckedInAt());
        verify(stateTransitionService).recordHotelGuestLifecycleEvent(booking, "HOTEL_GUEST_CHECKED_IN");
    }

    @Test
    @DisplayName("BD-09: Check-in fails with 400 when attempting to check in prior to arrival date")
    void testCheckInGuest_EarlyCheckIn_FailsWith400() {
        LocalDate futureDate = LocalDate.now().plusDays(2);
        Booking booking = Booking.builder()
                .bookingRef("BKG-CHECKIN-EARLY")
                .serviceType("HOTEL")
                .providerId(5L)
                .status("CONFIRMED")
                .checkInDate(futureDate)
                .checkOutDate(futureDate.plusDays(2))
                .build();

        when(bookingRepository.findByBookingRef("BKG-CHECKIN-EARLY")).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                guestLifecycleService.checkInGuest("BKG-CHECKIN-EARLY", 5L, "ROLE_HOTEL_MANAGER", false)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("prior to scheduled check-in arrival date"));
    }

    @Test
    @DisplayName("BD-09: Check-in fails with 400 when reservation is unpaid or pending payment")
    void testCheckInGuest_UnpaidReservation_FailsWith400() {
        Booking booking = Booking.builder()
                .bookingRef("BKG-CHECKIN-UNPAID")
                .serviceType("HOTEL")
                .providerId(5L)
                .status("PENDING_PAYMENT")
                .checkInDate(LocalDate.now())
                .build();

        when(bookingRepository.findByBookingRef("BKG-CHECKIN-UNPAID")).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                guestLifecycleService.checkInGuest("BKG-CHECKIN-UNPAID", 5L, "ROLE_HOTEL_MANAGER", false)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Manual confirmation of unpaid reservations is strictly prohibited"));
    }

    @Test
    @DisplayName("BD-09: Check-in fails with 403 when manager does not own the hotel property")
    void testCheckInGuest_WrongManager_FailsWith403() {
        Booking booking = Booking.builder()
                .bookingRef("BKG-CHECKIN-WRONG-MGR")
                .serviceType("HOTEL")
                .providerId(5L)
                .status("CONFIRMED")
                .checkInDate(LocalDate.now())
                .build();

        when(bookingRepository.findByBookingRef("BKG-CHECKIN-WRONG-MGR")).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                guestLifecycleService.checkInGuest("BKG-CHECKIN-WRONG-MGR", 99L, "ROLE_HOTEL_MANAGER", false)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("belonging to another hotel property"));
    }

    @Test
    @DisplayName("BD-09: Platform Administrator can check in any hotel reservation")
    void testCheckInGuest_Admin_Succeeds() {
        LocalDate today = LocalDate.now();
        Booking booking = Booking.builder()
                .bookingRef("BKG-CHECKIN-ADMIN")
                .serviceType("HOTEL")
                .providerId(5L)
                .status("CONFIRMED")
                .checkInDate(today)
                .checkOutDate(today.plusDays(2))
                .build();

        when(bookingRepository.findByBookingRef("BKG-CHECKIN-ADMIN")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingLifecycleOperationResponse response = guestLifecycleService.checkInGuest("BKG-CHECKIN-ADMIN", 1L, "ROLE_ADMIN", false);
        assertNotNull(response);
        assertEquals("CHECKED_IN", response.getNewStatus());
    }

    // =========================================================================
    // 3. Operational Guest Lifecycle - Check-out (BD-09)
    // =========================================================================

    @Test
    @DisplayName("BD-09: Check-out succeeds for an active checked-in reservation")
    void testCheckOutGuest_Success() {
        Booking booking = Booking.builder()
                .bookingRef("BKG-CHECKOUT-1")
                .serviceType("HOTEL")
                .providerId(5L)
                .status("CHECKED_IN")
                .checkedInAt(LocalDateTime.now().minusDays(1))
                .build();

        when(bookingRepository.findByBookingRef("BKG-CHECKOUT-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingLifecycleOperationResponse response = guestLifecycleService.checkOutGuest("BKG-CHECKOUT-1", 5L, "ROLE_HOTEL_MANAGER", false);

        assertNotNull(response);
        assertEquals("CHECKED_OUT", response.getNewStatus());
        assertEquals("CHECKED_IN", response.getPreviousStatus());
        assertNotNull(booking.getCheckedOutAt());
        verify(stateTransitionService).recordHotelGuestLifecycleEvent(booking, "HOTEL_GUEST_CHECKED_OUT");
    }

    @Test
    @DisplayName("BD-09: Check-out fails with 400 when reservation is not checked in")
    void testCheckOutGuest_NotCheckedIn_FailsWith400() {
        Booking booking = Booking.builder()
                .bookingRef("BKG-CHECKOUT-INVALID")
                .serviceType("HOTEL")
                .providerId(5L)
                .status("CONFIRMED")
                .build();

        when(bookingRepository.findByBookingRef("BKG-CHECKOUT-INVALID")).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                guestLifecycleService.checkOutGuest("BKG-CHECKOUT-INVALID", 5L, "ROLE_HOTEL_MANAGER", false)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Reservation must be in CHECKED_IN status"));
    }

    // =========================================================================
    // 4. Operational Guest Lifecycle - No-Show (BD-09, TBD-02)
    // =========================================================================

    @Test
    @DisplayName("BD-09: Marking No-Show succeeds on or after check-in date, restores inventory, and sets non-refundable")
    void testMarkNoShow_Success() {
        LocalDate today = LocalDate.now();
        Booking booking = Booking.builder()
                .bookingRef("BKG-NOSHOW-1")
                .serviceType("HOTEL")
                .providerId(5L)
                .status("CONFIRMED")
                .checkInDate(today)
                .checkOutDate(today.plusDays(2))
                .totalAmount(1200.0)
                .build();

        when(bookingRepository.findByBookingRef("BKG-NOSHOW-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.POST), any(), eq(java.util.Map.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(java.util.Map.of("status", "RELEASED"), org.springframework.http.HttpStatus.OK));

        BookingLifecycleOperationResponse response = guestLifecycleService.markNoShow("BKG-NOSHOW-1", 5L, "ROLE_HOTEL_MANAGER", false);

        assertNotNull(response);
        assertEquals("NO_SHOW", response.getNewStatus());
        assertEquals(0.0, booking.getRefundAmount());
        assertEquals("NON_REFUNDABLE", booking.getRefundStatus());
        assertNotNull(booking.getNoShowAt());
        verify(stateTransitionService).recordHotelGuestLifecycleEvent(booking, "HOTEL_BOOKING_NO_SHOW");
    }

    @Test
    @DisplayName("BD-09: Marking No-Show fails with 400 for a future reservation")
    void testMarkNoShow_FutureDate_FailsWith400() {
        LocalDate futureDate = LocalDate.now().plusDays(3);
        Booking booking = Booking.builder()
                .bookingRef("BKG-NOSHOW-FUTURE")
                .serviceType("HOTEL")
                .providerId(5L)
                .status("CONFIRMED")
                .checkInDate(futureDate)
                .checkOutDate(futureDate.plusDays(2))
                .build();

        when(bookingRepository.findByBookingRef("BKG-NOSHOW-FUTURE")).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                guestLifecycleService.markNoShow("BKG-NOSHOW-FUTURE", 5L, "ROLE_HOTEL_MANAGER", false)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("prior to scheduled check-in arrival date"));
    }

    @Test
    @DisplayName("BD-09: Marking No-Show fails with 400 if guest already checked in")
    void testMarkNoShow_AlreadyCheckedIn_FailsWith400() {
        Booking booking = Booking.builder()
                .bookingRef("BKG-NOSHOW-CHECKEDIN")
                .serviceType("HOTEL")
                .providerId(5L)
                .status("CHECKED_IN")
                .checkInDate(LocalDate.now())
                .build();

        when(bookingRepository.findByBookingRef("BKG-NOSHOW-CHECKEDIN")).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                guestLifecycleService.markNoShow("BKG-NOSHOW-CHECKEDIN", 5L, "ROLE_HOTEL_MANAGER", false)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Guest has already checked in"));
    }

    @Test
    @DisplayName("BD-09: Date resolution parses checkInDate from detailsJson string when field is null")
    void testResolveCheckInDate_FromDetailsJson() {
        Booking booking = Booking.builder()
                .detailsJson("Check-in: 2026-11-20 | Check-out: 2026-11-23")
                .build();

        assertEquals(LocalDate.of(2026, 11, 20), booking.resolveCheckInDate());
        assertEquals(LocalDate.of(2026, 11, 23), booking.resolveCheckOutDate());
    }
}
