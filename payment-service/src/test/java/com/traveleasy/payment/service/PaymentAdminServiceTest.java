package com.traveleasy.payment.service;

import com.traveleasy.payment.config.RazorpayConfig;
import com.traveleasy.payment.dto.DailyRevenueTrendResponse;
import com.traveleasy.payment.dto.PaymentStatsResponse;
import com.traveleasy.payment.repository.PaymentRepository;
import com.traveleasy.payment.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaymentAdminServiceTest {

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
                webhookEventRepository
        );
    }

    @Test
    @DisplayName("WP-04 Service: getAdminStats calculates Net Collections = Captured - Processed Refunds using exact BigDecimal")
    void testGetAdminStats_Normal() {
        when(paymentRepository.sumSuccessfulCapturedCollections("INR")).thenReturn(new BigDecimal("150000.00"));
        when(paymentRepository.sumProcessedRefunds("INR")).thenReturn(new BigDecimal("20000.00"));

        PaymentStatsResponse response = paymentService.getAdminStats();

        assertNotNull(response);
        assertEquals("INR", response.getCurrency());
        assertEquals(new BigDecimal("150000.00"), response.getSuccessfulCapturedCollections());
        assertEquals(new BigDecimal("20000.00"), response.getProcessedRefunds());
        assertEquals(new BigDecimal("130000.00"), response.getNetCollections());
    }

    @Test
    @DisplayName("WP-04 Service: getAdminStats handles fractional paise rounding consistently")
    void testGetAdminStats_FractionalPaiseRounding() {
        // Test values with fractional paise representations
        when(paymentRepository.sumSuccessfulCapturedCollections("INR")).thenReturn(new BigDecimal("12345.678"));
        when(paymentRepository.sumProcessedRefunds("INR")).thenReturn(new BigDecimal("2345.123"));

        PaymentStatsResponse response = paymentService.getAdminStats();

        assertNotNull(response);
        // 12345.678 -> 12345.68 (HALF_UP)
        assertEquals(new BigDecimal("12345.68"), response.getSuccessfulCapturedCollections());
        // 2345.123 -> 2345.12 (HALF_UP)
        assertEquals(new BigDecimal("2345.12"), response.getProcessedRefunds());
        // Net = 12345.68 - 2345.12 = 10000.56
        assertEquals(new BigDecimal("10000.56"), response.getNetCollections());
    }

    @Test
    @DisplayName("WP-04 Service: getAdminStats handles empty database safely")
    void testGetAdminStats_EmptyDatabase() {
        when(paymentRepository.sumSuccessfulCapturedCollections("INR")).thenReturn(null);
        when(paymentRepository.sumProcessedRefunds("INR")).thenReturn(null);

        PaymentStatsResponse response = paymentService.getAdminStats();

        assertNotNull(response);
        assertEquals("INR", response.getCurrency());
        assertEquals(new BigDecimal("0.00"), response.getSuccessfulCapturedCollections());
        assertEquals(new BigDecimal("0.00"), response.getProcessedRefunds());
        assertEquals(new BigDecimal("0.00"), response.getNetCollections());
    }

    @Test
    @DisplayName("WP-04 Service: getAdminRevenueTrends returns continuous 7-day timeline with zero-fill")
    void testGetAdminRevenueTrends_ZeroFill() {
        when(paymentRepository.countDailyCapturedNative(any(LocalDateTime.class), any(LocalDateTime.class), eq("INR")))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.countDailyRefundedNative(any(LocalDateTime.class), any(LocalDateTime.class), eq("INR")))
                .thenReturn(Collections.emptyList());

        List<DailyRevenueTrendResponse> trends = paymentService.getAdminRevenueTrends(7);

        assertNotNull(trends);
        assertEquals(7, trends.size());

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);

        for (int i = 0; i < 7; i++) {
            DailyRevenueTrendResponse item = trends.get(i);
            assertEquals(startDate.plusDays(i), item.getDate());
            assertEquals(new BigDecimal("0.00"), item.getCapturedCollections());
            assertEquals(new BigDecimal("0.00"), item.getProcessedRefunds());
            assertEquals(new BigDecimal("0.00"), item.getNetCollections());
            assertEquals("INR", item.getCurrency());
        }
    }

    @Test
    @DisplayName("WP-04 Service: getAdminRevenueTrends preserves negative daily net collections when refunds exceed captures")
    void testGetAdminRevenueTrends_NegativeDailyNetPreserved() {
        LocalDate today = LocalDate.now();
        java.sql.Date sqlToday = java.sql.Date.valueOf(today);

        List<Object[]> capturedRows = new ArrayList<>();
        capturedRows.add(new Object[]{sqlToday, new BigDecimal("1000.00")});

        List<Object[]> refundedRows = new ArrayList<>();
        refundedRows.add(new Object[]{sqlToday, new BigDecimal("4000.00")}); // Refund of 4000 exceeds capture of 1000

        when(paymentRepository.countDailyCapturedNative(any(LocalDateTime.class), any(LocalDateTime.class), eq("INR")))
                .thenReturn(capturedRows);
        when(paymentRepository.countDailyRefundedNative(any(LocalDateTime.class), any(LocalDateTime.class), eq("INR")))
                .thenReturn(refundedRows);

        List<DailyRevenueTrendResponse> trends = paymentService.getAdminRevenueTrends(7);

        assertNotNull(trends);
        DailyRevenueTrendResponse todayItem = trends.get(6); // Last item is today
        assertEquals(today, todayItem.getDate());
        assertEquals(new BigDecimal("1000.00"), todayItem.getCapturedCollections());
        assertEquals(new BigDecimal("4000.00"), todayItem.getProcessedRefunds());
        assertEquals(new BigDecimal("-3000.00"), todayItem.getNetCollections()); // Must NOT be clamped to 0!
    }

    @Test
    @DisplayName("WP-04 Service: getAdminRevenueTrends bounds days between 1 and 90")
    void testGetAdminRevenueTrends_BoundedDays() {
        when(paymentRepository.countDailyCapturedNative(any(LocalDateTime.class), any(LocalDateTime.class), eq("INR")))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.countDailyRefundedNative(any(LocalDateTime.class), any(LocalDateTime.class), eq("INR")))
                .thenReturn(Collections.emptyList());

        // Test days < 1 clamped to 1
        List<DailyRevenueTrendResponse> trends1 = paymentService.getAdminRevenueTrends(0);
        assertEquals(1, trends1.size());

        // Test days > 90 clamped to 90
        List<DailyRevenueTrendResponse> trends90 = paymentService.getAdminRevenueTrends(120);
        assertEquals(90, trends90.size());
    }
}
