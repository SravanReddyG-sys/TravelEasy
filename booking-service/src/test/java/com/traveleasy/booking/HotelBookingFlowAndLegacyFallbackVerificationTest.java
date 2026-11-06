package com.traveleasy.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.controller.BookingController;
import com.traveleasy.booking.dto.CancellationPolicyResult;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.service.*;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
public class HotelBookingFlowAndLegacyFallbackVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private BookingInternalTokenService internalTokenService;

    @MockBean
    private HotelCancellationPolicyService cancellationPolicyService;

    @MockBean
    private HotelGuestLifecycleService guestLifecycleService;

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private BookingController bookingController;

    @BeforeEach
    void setUp() {
        bookingController.setRestTemplate(restTemplate);
        when(securityService.validateToken(anyString())).thenReturn(true);
        when(securityService.extractUserId(anyString())).thenReturn(10L);
        when(securityService.extractRole(anyString())).thenReturn("ROLE_CUSTOMER");
        when(internalTokenService.generateInternalToken()).thenReturn("mock-internal-token");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stateTransitionService.cancelBooking(any(), any(), any())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setStatus("CANCELLED");
            return b;
        });
    }

    // =========================================================================
    // AREA 1: BOOKING CONFIRMATION & EXPIRED HOLD SAFEGUARDS
    // =========================================================================

    @Test
    @DisplayName("AREA 1.1: Confirm date-wise hotel booking calls confirm-stay in hotel-service and transitions to CONFIRMED")
    void testConfirmBooking_DateWiseHotel_ConfirmsStayInHotelService() throws Exception {
        Booking dateWiseBooking = Booking.builder()
                .id(101L)
                .bookingRef("BKG-HOTEL-OK")
                .customerId(10L)
                .serviceType("HOTEL")
                .serviceId(1L)
                .totalAmount(2500.0)
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .status("PENDING_PAYMENT")
                .build();

        when(bookingRepository.findById(101L)).thenReturn(Optional.of(dateWiseBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stateTransitionService.confirmBooking(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setStatus("CONFIRMED");
            return b;
        });

        // 1. Payment verification returns SUCCESS
        when(restTemplate.getForEntity(contains("/api/payments/booking/BKG-HOTEL-OK"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "SUCCESS", "amount", 2500.0), HttpStatus.OK));

        // 2. Hotel service confirm-stay returns 200 OK
        when(restTemplate.exchange(contains("/api/hotels/rooms/confirm-stay?bookingRef=BKG-HOTEL-OK"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "CONFIRMED", "message", "Reservation confirmed successfully"), HttpStatus.OK));

        mockMvc.perform(put("/api/bookings/101/confirm")
                        .header("Authorization", "Bearer valid-cust-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Booking confirmed"))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));

        // Verify confirm-stay was called in hotel-service
        verify(restTemplate).exchange(contains("/api/hotels/rooms/confirm-stay?bookingRef=BKG-HOTEL-OK"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("AREA 1.2: Late payment when room hold expired in hotel-service triggers refund and returns 409 Conflict")
    void testConfirmBooking_LatePayment_RoomHoldExpired_TriggersRefundAndReturns409() throws Exception {
        Booking dateWiseBooking = Booking.builder()
                .id(102L)
                .bookingRef("BKG-HOTEL-LATE")
                .customerId(10L)
                .serviceType("HOTEL")
                .serviceId(1L)
                .totalAmount(3000.0)
                .checkInDate(LocalDate.now().plusDays(3))
                .checkOutDate(LocalDate.now().plusDays(5))
                .status("PENDING_PAYMENT")
                .build();

        when(bookingRepository.findById(102L)).thenReturn(Optional.of(dateWiseBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Payment verified SUCCESS
        when(restTemplate.getForEntity(contains("/api/payments/booking/BKG-HOTEL-LATE"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "SUCCESS", "amount", 3000.0), HttpStatus.OK));

        // 2. Hotel service confirm-stay throws 400 Bad Request because hold was already RELEASED
        when(restTemplate.exchange(contains("/api/hotels/rooms/confirm-stay?bookingRef=BKG-HOTEL-LATE"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Cannot confirm allocation with status: RELEASED"));

        // 3. Payment refund mock
        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-HOTEL-LATE"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("refundId", "rfnd_HOTEL_AUTO_1", "status", "SUCCESS"), HttpStatus.OK));

        mockMvc.perform(put("/api/bookings/102/confirm")
                        .header("Authorization", "Bearer valid-cust-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Room hold has expired and room inventory is no longer available")))
                .andExpect(jsonPath("$.booking.status").value("EXPIRED"));

        // Verify refund was initiated in payment-service
        verify(restTemplate).exchange(contains("/api/payments/refund/booking/BKG-HOTEL-LATE"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
        verify(stateTransitionService, never()).confirmBooking(any());
    }

    @Test
    @DisplayName("AREA 1.3: Confirm booking rejects already EXPIRED booking with 400 Bad Request")
    void testConfirmBooking_AlreadyExpired_RejectsWithBadRequest() throws Exception {
        Booking expiredBooking = Booking.builder()
                .id(103L)
                .bookingRef("BKG-HOTEL-EXPIRED")
                .customerId(10L)
                .serviceType("HOTEL")
                .serviceId(1L)
                .status("EXPIRED")
                .build();

        when(bookingRepository.findById(103L)).thenReturn(Optional.of(expiredBooking));

        mockMvc.perform(put("/api/bookings/103/confirm")
                        .header("Authorization", "Bearer valid-cust-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot confirm an expired booking"));

        verify(restTemplate, never()).getForEntity(anyString(), any());
    }

    // =========================================================================
    // AREA 2: LEGACY INVENTORY FALLBACK GUARDS
    // =========================================================================

    @Test
    @DisplayName("AREA 2.1: Date-wise booking cancellation invokes ONLY release-stay and never calls legacy count release")
    void testDateWiseBooking_Cancellation_InvokesOnlyDateWiseRelease() throws Exception {
        Booking dateWiseBooking = Booking.builder()
                .id(201L)
                .bookingRef("BKG-DATEWISE-CANCEL")
                .customerId(10L)
                .serviceType("HOTEL")
                .serviceId(1L)
                .totalAmount(4000.0)
                .checkInDate(LocalDate.now().plusDays(10))
                .checkOutDate(LocalDate.now().plusDays(12))
                .status("CONFIRMED")
                .build();

        when(bookingRepository.findById(201L)).thenReturn(Optional.of(dateWiseBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Cancellation policy: 100% refund tier
        CancellationPolicyResult policyResult = CancellationPolicyResult.builder()
                .policyTier("FREE_CANCELLATION_48H")
                .refundPercentage(100.0)
                .refundAmount(4000.0)
                .cancellationFee(0.0)
                .eligibleForRefund(true)
                .description("Full refund")
                .build();
        when(cancellationPolicyService.evaluateCancellationPolicy(any(), any())).thenReturn(policyResult);
        when(cancellationPolicyService.buildPolicySnapshotJson(any())).thenReturn("{\"policyTier\":\"FREE_CANCELLATION_48H\"}");

        // Payment refund mock
        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-DATEWISE-CANCEL"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("refundId", "rfnd_CANCEL_100", "status", "SUCCESS"), HttpStatus.OK));

        // Date-wise stay release mock
        when(restTemplate.exchange(contains("/api/hotels/rooms/release-stay?bookingRef=BKG-DATEWISE-CANCEL"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "RELEASED", "idempotent", true), HttpStatus.OK));

        mockMvc.perform(put("/api/bookings/201/cancel")
                        .header("Authorization", "Bearer valid-cust-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("Booking cancelled successfully")));

        // Verify date-wise release-stay was called
        verify(restTemplate).exchange(contains("/api/hotels/rooms/release-stay?bookingRef=BKG-DATEWISE-CANCEL"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));

        // CRITICAL: Verify legacy count release endpoint was NEVER invoked!
        verify(restTemplate, never()).exchange(contains("/release?count="), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("AREA 2.2: Date-wise booking release-stay exception must NEVER fall back to legacy count release")
    void testDateWiseBooking_ReleaseStayFails_DoesNotInvokeLegacyFallback() throws Exception {
        Booking dateWiseBooking = Booking.builder()
                .id(202L)
                .bookingRef("BKG-DATEWISE-ERR")
                .customerId(10L)
                .serviceType("HOTEL")
                .serviceId(1L)
                .totalAmount(2000.0)
                .checkInDate(LocalDate.now().plusDays(4))
                .checkOutDate(LocalDate.now().plusDays(6))
                .status("CONFIRMED")
                .build();

        when(bookingRepository.findById(202L)).thenReturn(Optional.of(dateWiseBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        CancellationPolicyResult policyResult = CancellationPolicyResult.builder()
                .policyTier("FREE_CANCELLATION_48H")
                .refundPercentage(100.0)
                .refundAmount(2000.0)
                .cancellationFee(0.0)
                .eligibleForRefund(true)
                .description("Full refund")
                .build();
        when(cancellationPolicyService.evaluateCancellationPolicy(any(), any())).thenReturn(policyResult);
        when(cancellationPolicyService.buildPolicySnapshotJson(any())).thenReturn("{\"policyTier\":\"FREE_CANCELLATION_48H\"}");

        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-DATEWISE-ERR"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("refundId", "rfnd_ERR_1", "status", "SUCCESS"), HttpStatus.OK));

        // Simulate hotel-service throwing 500 or network timeout on release-stay
        when(restTemplate.exchange(contains("/api/hotels/rooms/release-stay?bookingRef=BKG-DATEWISE-ERR"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection timed out to hotel-service"));

        mockMvc.perform(put("/api/bookings/202/cancel")
                        .header("Authorization", "Bearer valid-cust-token"))
                .andExpect(status().isOk());

        // CRITICAL: Even though release-stay threw an exception, date-wise bookings MUST NOT fall back to legacy count release!
        verify(restTemplate, never()).exchange(contains("/release?count="), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("AREA 2.3: Genuine legacy booking (no stay dates) invokes legacy count release path")
    void testGenuineLegacyBooking_Cancellation_InvokesLegacyCountRelease() throws Exception {
        Booking legacyBooking = Booking.builder()
                .id(203L)
                .bookingRef("BKG-LEGACY-001")
                .customerId(10L)
                .serviceType("HOTEL")
                .serviceId(5L) // roomId in legacy model
                .totalAmount(1500.0)
                .detailsJson("Rooms: 2") // No check-in / check-out dates
                .checkInDate(null)
                .checkOutDate(null)
                .status("CONFIRMED")
                .build();

        when(bookingRepository.findById(203L)).thenReturn(Optional.of(legacyBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        CancellationPolicyResult policyResult = CancellationPolicyResult.builder()
                .policyTier("FREE_CANCELLATION_48H")
                .refundPercentage(100.0)
                .refundAmount(1500.0)
                .cancellationFee(0.0)
                .eligibleForRefund(true)
                .description("Legacy refund")
                .build();
        when(cancellationPolicyService.evaluateCancellationPolicy(any(), any())).thenReturn(policyResult);
        when(cancellationPolicyService.buildPolicySnapshotJson(any())).thenReturn("{\"policyTier\":\"FREE_CANCELLATION_48H\"}");

        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-LEGACY-001"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("refundId", "rfnd_LEGACY_1", "status", "SUCCESS"), HttpStatus.OK));

        // Legacy count release mock
        when(restTemplate.exchange(contains("/api/hotels/rooms/5/release?count=2"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("message", "Rooms released successfully"), HttpStatus.OK));

        mockMvc.perform(put("/api/bookings/203/cancel")
                        .header("Authorization", "Bearer valid-cust-token"))
                .andExpect(status().isOk());

        // Verify release-stay was NOT called (because it is not a date-wise booking)
        verify(restTemplate, never()).exchange(contains("/api/hotels/rooms/release-stay"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));

        // Verify legacy release path WAS called with roomId 5 and count 2
        verify(restTemplate).exchange(contains("/api/hotels/rooms/5/release?count=2"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class));
    }
}
