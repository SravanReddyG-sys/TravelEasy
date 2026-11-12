package com.traveleasy.payment.service;

import com.traveleasy.payment.config.RazorpayConfig;
import com.traveleasy.payment.dto.HotelRevenueReportRequest;
import com.traveleasy.payment.dto.HotelRevenueReportResponse;
import com.traveleasy.payment.exception.PaymentException;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.repository.PaymentRepository;
import com.traveleasy.payment.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HotelRevenueReportingTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RazorpayConfig razorpayConfig;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PaymentStateTransitionService stateTransitionService;

    @Mock
    private PaymentRecipientResolutionService recipientResolutionService;

    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentInternalTokenService internalTokenService;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                razorpayConfig,
                null,
                restTemplate,
                stateTransitionService,
                recipientResolutionService,
                webhookEventRepository,
                internalTokenService
        );
        paymentService.setHotelServiceUrl("http://localhost:8083");
        paymentService.setBookingServiceUrl("http://localhost:8084");

        lenient().when(internalTokenService.generateInternalToken()).thenReturn("internal-jwt-token");
        lenient().when(restTemplate.exchange(
                eq("http://localhost:8083/api/hotels/internal/1"),
                eq(HttpMethod.GET),
                any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("id", 1L, "managerId", 3L)));
        lenient().when(restTemplate.exchange(
                eq("http://localhost:8084/api/bookings/hotel/1/refs"),
                eq(HttpMethod.GET),
                any(),
                eq(List.class)
        )).thenReturn(ResponseEntity.ok(List.of(
                "BKG-001", "BKG-PEND-01", "BKG-PEND-02", "BKG-FAIL-01",
                "BKG-REF-01", "BKG-PARTIAL-01", "BKG-DUP-01", "BKG-OCT-01", "BKG-NOV-01"
        )));
    }

    @Test
    @DisplayName("1. Successfully captured payment contributes to revenue in its capture period")
    void testSuccessfullyCapturedPayment_ContributesToRevenueInCapturePeriod() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .bookingRef("BKG-001")
                .amount(5000.0)
                .status("SUCCESS")
                .timestamp(LocalDateTime.of(2026, 11, 3, 14, 30))
                .build();

        when(paymentRepository.findByBookingRefIn(anyList())).thenReturn(List.of(txn));

        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(List.of("BKG-001"))
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 4))
                .build();

        HotelRevenueReportResponse response = paymentService.getHotelRevenueSummary(request, "ROLE_HOTEL_MANAGER", 3L);

        assertEquals(5000.0, response.getSuccessfulPaymentsAmount());
        assertEquals(1, response.getSuccessfulPaymentsCount());
        assertEquals(0.0, response.getProcessedRefundsAmount());
        assertEquals(5000.0, response.getNetRevenue());
    }

    @Test
    @DisplayName("2. Pending payment (CREATED, INITIATED) does not contribute to revenue")
    void testPendingPayment_DoesNotContributeToRevenue() {
        PaymentTransaction txn1 = PaymentTransaction.builder()
                .bookingRef("BKG-PEND-01")
                .amount(4000.0)
                .status("CREATED")
                .timestamp(LocalDateTime.of(2026, 11, 2, 10, 0))
                .build();

        PaymentTransaction txn2 = PaymentTransaction.builder()
                .bookingRef("BKG-PEND-02")
                .amount(3500.0)
                .status("INITIATED")
                .timestamp(LocalDateTime.of(2026, 11, 3, 11, 0))
                .build();

        when(paymentRepository.findByBookingRefIn(anyList())).thenReturn(List.of(txn1, txn2));

        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(List.of("BKG-PEND-01", "BKG-PEND-02"))
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 7))
                .build();

        HotelRevenueReportResponse response = paymentService.getHotelRevenueSummary(request, "ROLE_HOTEL_MANAGER", 3L);

        assertEquals(0.0, response.getSuccessfulPaymentsAmount());
        assertEquals(0, response.getSuccessfulPaymentsCount());
        assertEquals(0.0, response.getNetRevenue());
    }

    @Test
    @DisplayName("3. Failed payment does not contribute to revenue")
    void testFailedPayment_DoesNotContributeToRevenue() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .bookingRef("BKG-FAIL-01")
                .amount(5000.0)
                .status("FAILED")
                .timestamp(LocalDateTime.of(2026, 11, 3, 14, 30))
                .build();

        when(paymentRepository.findByBookingRefIn(anyList())).thenReturn(List.of(txn));

        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(List.of("BKG-FAIL-01"))
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 7))
                .build();

        HotelRevenueReportResponse response = paymentService.getHotelRevenueSummary(request, "ROLE_HOTEL_MANAGER", 3L);

        assertEquals(0.0, response.getSuccessfulPaymentsAmount());
        assertEquals(0, response.getSuccessfulPaymentsCount());
        assertEquals(0.0, response.getNetRevenue());
    }

    @Test
    @DisplayName("4. Successful refund is deducted in its actual completion period (Nov 7-8: -5000)")
    void testSuccessfulRefund_DeductedInActualCompletionPeriod() {
        // Payment captured Nov 3: 5000, Refund processed Nov 7: 5000
        PaymentTransaction txn = PaymentTransaction.builder()
                .bookingRef("BKG-REF-01")
                .amount(5000.0)
                .status("REFUNDED")
                .timestamp(LocalDateTime.of(2026, 11, 3, 14, 30))
                .refundId("rfnd_12345")
                .refundStatus("PROCESSED")
                .refundAmount(5000.0)
                .refundedAt(LocalDateTime.of(2026, 11, 7, 16, 0))
                .build();

        when(paymentRepository.findByBookingRefIn(anyList())).thenReturn(List.of(txn));

        // Period Nov 7–8: Capture date (Nov 3) is outside range; Refund date (Nov 7) is inside range
        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(List.of("BKG-REF-01"))
                .startDate(LocalDate.of(2026, 11, 7))
                .endDate(LocalDate.of(2026, 11, 8))
                .build();

        HotelRevenueReportResponse response = paymentService.getHotelRevenueSummary(request, "ROLE_HOTEL_MANAGER", 3L);

        assertEquals(0.0, response.getSuccessfulPaymentsAmount());
        assertEquals(0, response.getSuccessfulPaymentsCount());
        assertEquals(5000.0, response.getProcessedRefundsAmount());
        assertEquals(1, response.getProcessedRefundsCount());
        assertEquals(-5000.0, response.getNetRevenue());
    }

    @Test
    @DisplayName("5. Cancellation before refund completion does not prematurely deduct revenue (Nov 5-6: 0)")
    void testCancellationBeforeRefundCompletion_DoesNotPrematurelyDeductRevenue() {
        // Booking cancelled Nov 5, but refund not completed until Nov 7
        PaymentTransaction txn = PaymentTransaction.builder()
                .bookingRef("BKG-REF-01")
                .amount(5000.0)
                .status("REFUND_INITIATED")
                .timestamp(LocalDateTime.of(2026, 11, 3, 14, 30))
                .refundId("rfnd_12345")
                .refundStatus("INITIATED") // Not yet PROCESSED
                .refundAmount(5000.0)
                .refundedAt(null)
                .build();

        when(paymentRepository.findByBookingRefIn(anyList())).thenReturn(List.of(txn));

        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(List.of("BKG-REF-01"))
                .startDate(LocalDate.of(2026, 11, 5))
                .endDate(LocalDate.of(2026, 11, 6))
                .build();

        HotelRevenueReportResponse response = paymentService.getHotelRevenueSummary(request, "ROLE_HOTEL_MANAGER", 3L);

        assertEquals(0.0, response.getSuccessfulPaymentsAmount(), "Capture date Nov 3 is outside range");
        assertEquals(0.0, response.getProcessedRefundsAmount(), "Unprocessed refund must not be deducted");
        assertEquals(0.0, response.getNetRevenue());
    }

    @Test
    @DisplayName("6. Partial refund deducts only the successfully refunded amount")
    void testPartialRefund_DeductsOnlySuccessfullyRefundedAmount() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .bookingRef("BKG-PARTIAL-01")
                .amount(5000.0)
                .status("SUCCESS")
                .timestamp(LocalDateTime.of(2026, 11, 2, 10, 0))
                .refundId("rfnd_part_999")
                .refundStatus("PROCESSED")
                .refundAmount(2000.0) // 2000 refunded, 3000 retained
                .refundedAt(LocalDateTime.of(2026, 11, 4, 15, 0))
                .build();

        when(paymentRepository.findByBookingRefIn(anyList())).thenReturn(List.of(txn));

        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(List.of("BKG-PARTIAL-01"))
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 7))
                .build();

        HotelRevenueReportResponse response = paymentService.getHotelRevenueSummary(request, "ROLE_HOTEL_MANAGER", 3L);

        assertEquals(5000.0, response.getSuccessfulPaymentsAmount());
        assertEquals(2000.0, response.getProcessedRefundsAmount());
        assertEquals(3000.0, response.getNetRevenue());
    }

    @Test
    @DisplayName("7. Duplicate refund events do not deduct the same refund twice")
    void testDuplicateRefundEvents_DoNotDeductTwice() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .bookingRef("BKG-DUP-01")
                .amount(6000.0)
                .status("REFUNDED")
                .timestamp(LocalDateTime.of(2026, 11, 1, 9, 0))
                .refundId("rfnd_dup_1")
                .refundStatus("PROCESSED")
                .refundAmount(6000.0)
                .refundedAt(LocalDateTime.of(2026, 11, 5, 12, 0))
                .build();

        when(paymentRepository.findByBookingRefIn(anyList())).thenReturn(List.of(txn));

        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(List.of("BKG-DUP-01"))
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 7))
                .build();

        HotelRevenueReportResponse response = paymentService.getHotelRevenueSummary(request, "ROLE_HOTEL_MANAGER", 3L);

        assertEquals(6000.0, response.getSuccessfulPaymentsAmount());
        assertEquals(6000.0, response.getProcessedRefundsAmount());
        assertEquals(1, response.getProcessedRefundsCount());
        assertEquals(0.0, response.getNetRevenue());
    }

    @Test
    @DisplayName("8. Current and previous reporting periods produce correct independent totals")
    void testCurrentAndPreviousReportingPeriods_ProduceIndependentTotals() {
        PaymentTransaction prevTxn = PaymentTransaction.builder()
                .bookingRef("BKG-OCT-01")
                .amount(4500.0)
                .status("SUCCESS")
                .timestamp(LocalDateTime.of(2026, 10, 31, 11, 0))
                .build();

        PaymentTransaction currTxn = PaymentTransaction.builder()
                .bookingRef("BKG-NOV-01")
                .amount(7000.0)
                .status("SUCCESS")
                .timestamp(LocalDateTime.of(2026, 11, 3, 14, 0))
                .build();

        when(paymentRepository.findByBookingRefIn(anyList())).thenReturn(List.of(prevTxn, currTxn));

        // Current period: Nov 1 - Nov 7
        HotelRevenueReportRequest currRequest = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(List.of("BKG-OCT-01", "BKG-NOV-01"))
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 7))
                .build();

        HotelRevenueReportResponse currResponse = paymentService.getHotelRevenueSummary(currRequest, "ROLE_HOTEL_MANAGER", 3L);
        assertEquals(7000.0, currResponse.getSuccessfulPaymentsAmount());
        assertEquals(7000.0, currResponse.getNetRevenue());

        // Previous period: Oct 25 - Oct 31
        HotelRevenueReportRequest prevRequest = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(List.of("BKG-OCT-01", "BKG-NOV-01"))
                .startDate(LocalDate.of(2026, 10, 25))
                .endDate(LocalDate.of(2026, 10, 31))
                .build();

        HotelRevenueReportResponse prevResponse = paymentService.getHotelRevenueSummary(prevRequest, "ROLE_HOTEL_MANAGER", 3L);
        assertEquals(4500.0, prevResponse.getSuccessfulPaymentsAmount());
        assertEquals(4500.0, prevResponse.getNetRevenue());
    }

    @Test
    @DisplayName("9. Non-owner hotel manager is rejected with 403 FORBIDDEN without querying payments")
    void testNonOwnerManager_Rejected403() {
        when(restTemplate.exchange(
                eq("http://localhost:8083/api/hotels/internal/99"),
                eq(HttpMethod.GET),
                any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("id", 99L, "managerId", 500L)));

        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(99L)
                .bookingRefs(List.of("BKG-OTHER-1"))
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 7))
                .build();

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.getHotelRevenueSummary(request, "ROLE_HOTEL_MANAGER", 3L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(paymentRepository, never()).findByBookingRefIn(anyList());
    }

    @Test
    @DisplayName("10. Tampered cross-hotel or bus bookingRefs are rejected with 403 FORBIDDEN")
    void testTamperedCrossPropertyBookingRefs_Rejected403() {
        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(List.of("BKG-001", "BUS-BOOKING-999"))
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 7))
                .build();

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.getHotelRevenueSummary(request, "ROLE_HOTEL_MANAGER", 3L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(paymentRepository, never()).findByBookingRefIn(anyList());
    }

    @Test
    @DisplayName("11. Missing bookingRefs resolves authoritative bookingRefs from booking-service")
    void testEmptyBookingRefs_ResolvesAuthoritativeBookingRefs() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(101L)
                .bookingRef("BKG-001")
                .amount(6200.0)
                .status("SUCCESS")
                .timestamp(LocalDateTime.of(2026, 11, 2, 10, 0))
                .build();
        when(paymentRepository.findByBookingRefIn(anyList())).thenReturn(List.of(txn, txn)); // Also verify deduplication!

        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(Collections.emptyList())
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 7))
                .build();

        HotelRevenueReportResponse response = paymentService.getHotelRevenueSummary(request, "ROLE_ADMIN", 1L);
        assertEquals(6200.0, response.getSuccessfulPaymentsAmount());
        assertEquals(1, response.getSuccessfulPaymentsCount());
        assertEquals(6200.0, response.getNetRevenue());
    }

    @Test
    @DisplayName("12. Missing or invalid hotelId fails closed with 400 BAD_REQUEST")
    void testInvalidHotelId_Rejected400() {
        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(null)
                .bookingRefs(List.of("BKG-001"))
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 7))
                .build();

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.getHotelRevenueSummary(request, "ROLE_HOTEL_MANAGER", 3L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    @DisplayName("13. Booking service lookup failure fails closed with 503 SERVICE_UNAVAILABLE")
    void testBookingRefsLookupFailure_FailsClosed503() {
        when(restTemplate.exchange(
                contains("/api/bookings/hotel/1/refs"),
                eq(HttpMethod.GET),
                any(),
                eq(List.class)
        )).thenThrow(new RuntimeException("booking-service timeout"));

        HotelRevenueReportRequest request = HotelRevenueReportRequest.builder()
                .hotelId(1L)
                .bookingRefs(List.of("BKG-001"))
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 7))
                .build();

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentService.getHotelRevenueSummary(request, "ROLE_HOTEL_MANAGER", 3L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        verify(paymentRepository, never()).findByBookingRefIn(anyList());
    }
}
