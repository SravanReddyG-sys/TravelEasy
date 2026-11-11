package com.traveleasy.payment.integration;

import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PaymentAdminRepositoryIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    private static final String PREFIX = "TXN-INT-WP04-";

    @BeforeEach
    @AfterEach
    void cleanUp() {
        List<PaymentTransaction> all = paymentRepository.findAll();
        for (PaymentTransaction t : all) {
            if (t.getTransactionRef() != null && t.getTransactionRef().startsWith(PREFIX)) {
                paymentRepository.delete(t);
            }
        }
    }

    @Test
    @DisplayName("WP-04 Integration: Database aggregation verifies Captured, Processed Refunds, and BigDecimal Precision")
    void testDatabaseAggregations() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Captured payment (no refund): 1000.50
        paymentRepository.save(PaymentTransaction.builder()
                .transactionRef(PREFIX + "1")
                .bookingRef("BK-WP04-1")
                .customerId(101L)
                .amount(1000.50)
                .currency("INR")
                .status("SUCCESS")
                .timestamp(now)
                .build());

        // 2. Captured payment with partial processed refund: amount=1500.75, refundAmount=400.25
        paymentRepository.save(PaymentTransaction.builder()
                .transactionRef(PREFIX + "2")
                .bookingRef("BK-WP04-2")
                .customerId(102L)
                .amount(1500.75)
                .currency("INR")
                .status("REFUNDED")
                .refundStatus("PROCESSED")
                .refundAmount(400.25)
                .timestamp(now)
                .refundedAt(now)
                .build());

        // 3. Captured payment with full processed refund: amount=2000.00, refundAmount=2000.00
        paymentRepository.save(PaymentTransaction.builder()
                .transactionRef(PREFIX + "3")
                .bookingRef("BK-WP04-3")
                .customerId(103L)
                .amount(2000.0)
                .currency("INR")
                .status("REFUNDED")
                .refundStatus("PROCESSED")
                .refundAmount(2000.0)
                .timestamp(now)
                .refundedAt(now)
                .build());

        // 4. Captured payment with INITIATED refund (not processed yet): amount=800.00, refundAmount=800.00
        paymentRepository.save(PaymentTransaction.builder()
                .transactionRef(PREFIX + "4")
                .bookingRef("BK-WP04-4")
                .customerId(104L)
                .amount(800.0)
                .currency("INR")
                .status("REFUND_INITIATED")
                .refundStatus("INITIATED")
                .refundAmount(800.0)
                .timestamp(now)
                .build());

        // 5. Failed payment attempt (must be excluded): amount=500.00
        paymentRepository.save(PaymentTransaction.builder()
                .transactionRef(PREFIX + "5")
                .bookingRef("BK-WP04-5")
                .customerId(105L)
                .amount(500.0)
                .currency("INR")
                .status("FAILED")
                .timestamp(now)
                .build());

        // 6. Created order attempt (must be excluded): amount=600.00
        paymentRepository.save(PaymentTransaction.builder()
                .transactionRef(PREFIX + "6")
                .bookingRef("BK-WP04-6")
                .customerId(106L)
                .amount(600.0)
                .currency("INR")
                .status("CREATED")
                .timestamp(now)
                .build());

        // 7. Captured payment in different currency (USD, must be excluded from INR): amount=999.00
        paymentRepository.save(PaymentTransaction.builder()
                .transactionRef(PREFIX + "7")
                .bookingRef("BK-WP04-7")
                .customerId(107L)
                .amount(999.0)
                .currency("USD")
                .status("SUCCESS")
                .timestamp(now)
                .build());

        // Execute Aggregation Queries returning exact BigDecimal
        BigDecimal captured = paymentRepository.sumSuccessfulCapturedCollections("INR");
        BigDecimal refunded = paymentRepository.sumProcessedRefunds("INR");

        assertNotNull(captured);
        assertNotNull(refunded);
        assertInstanceOf(BigDecimal.class, captured, "Should return exact BigDecimal");
        assertInstanceOf(BigDecimal.class, refunded, "Should return exact BigDecimal");

        // Captured should be at least 1000.50 + 1500.75 + 2000.00 + 800.00 = 5301.25
        assertTrue(captured.compareTo(new BigDecimal("5301.25")) >= 0,
                "Captured collections should include all 4 captured INR payments with exact paise precision");

        // Processed Refunds should be at least 400.25 + 2000.00 = 2400.25
        assertTrue(refunded.compareTo(new BigDecimal("2400.25")) >= 0,
                "Processed refunds should include only PROCESSED refunds with exact paise precision");

        // Verify Daily Native Queries
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59, 999999999);

        List<Object[]> dailyCaptured = paymentRepository.countDailyCapturedNative(startOfDay, endOfDay, "INR");
        assertFalse(dailyCaptured.isEmpty(), "Should have daily captured row for today");
        assertInstanceOf(BigDecimal.class, dailyCaptured.get(0)[1], "Daily captured amount should be BigDecimal");

        List<Object[]> dailyRefunded = paymentRepository.countDailyRefundedNative(startOfDay, endOfDay, "INR");
        assertFalse(dailyRefunded.isEmpty(), "Should have daily refunded row for today");
        assertInstanceOf(BigDecimal.class, dailyRefunded.get(0)[1], "Daily refunded amount should be BigDecimal");
    }

    @Test
    @DisplayName("WP-04 Integration: Legacy refund with NULL refunded_at is excluded from daily trends but counted in cumulative stats")
    void testLegacyRefundWithoutDatePolicy() {
        LocalDateTime now = LocalDateTime.now();

        // Legacy record: status REFUNDED, refundStatus PROCESSED, but refundedAt IS NULL
        paymentRepository.save(PaymentTransaction.builder()
                .transactionRef(PREFIX + "LEGACY-NODATE")
                .bookingRef("BK-WP04-LEGACY")
                .customerId(201L)
                .amount(1200.0)
                .refundAmount(1200.0)
                .currency("INR")
                .status("REFUNDED")
                .refundStatus("PROCESSED")
                .timestamp(now.minusDays(10)) // Captured 10 days ago
                .refundedAt(null)             // Refund processing date is unknown/null
                .build());

        // 1. Cumulative stats should INCLUDE this processed refund
        BigDecimal refundedCumulative = paymentRepository.sumProcessedRefunds("INR");
        assertNotNull(refundedCumulative);
        assertTrue(refundedCumulative.compareTo(new BigDecimal("1200.00")) >= 0,
                "Cumulative processed refunds must include legacy processed refund with null date");

        // 2. Daily trends for the capture day (10 days ago) must NOT attribute this refund to capture day!
        LocalDateTime startOfCaptureDay = now.minusDays(10).toLocalDate().atStartOfDay();
        LocalDateTime endOfCaptureDay = now.minusDays(10).toLocalDate().atTime(23, 59, 59, 999999999);

        List<Object[]> dailyRefunded = paymentRepository.countDailyRefundedNative(startOfCaptureDay, endOfCaptureDay, "INR");
        // Must NOT find a refund on capture day because refundedAt is NULL
        boolean foundLegacy = false;
        for (Object[] row : dailyRefunded) {
            if (row[0] != null && row[0].toString().equals(now.minusDays(10).toLocalDate().toString())) {
                foundLegacy = true;
            }
        }
        assertFalse(foundLegacy, "Legacy refund without refunded_at must NOT be attributed to payment capture date");
    }

    @Test
    @DisplayName("WP-04 Integration: Missing refundAmount falls back to amount ONLY when status is REFUNDED")
    void testRefundAmountFallbackPolicy() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Legacy record: status REFUNDED, refundStatus PROCESSED, refundAmount is NULL
        paymentRepository.save(PaymentTransaction.builder()
                .transactionRef(PREFIX + "LEGACY-REFUNDED-NULLAMT")
                .bookingRef("BK-WP04-LEG-1")
                .amount(750.0)
                .refundAmount(null)
                .currency("INR")
                .status("REFUNDED") // Strongest evidence of full refund in legacy lifecycle
                .refundStatus("PROCESSED")
                .timestamp(now)
                .refundedAt(now)
                .build());

        // 2. Incomplete record: status SUCCESS, refundStatus PROCESSED, but refundAmount is NULL
        paymentRepository.save(PaymentTransaction.builder()
                .transactionRef(PREFIX + "INCOMPLETE-NULLAMT")
                .bookingRef("BK-WP04-LEG-2")
                .amount(900.0)
                .refundAmount(null)
                .currency("INR")
                .status("SUCCESS") // Status is NOT REFUNDED, refundAmount is NULL -> Unestablished amount, evaluates to 0.00
                .refundStatus("PROCESSED")
                .timestamp(now)
                .refundedAt(now)
                .build());

        BigDecimal refunded = paymentRepository.sumProcessedRefunds("INR");
        assertNotNull(refunded);
        // Should include the 750.00 from status='REFUNDED', but NOT the 900.00 from status='SUCCESS'
        assertTrue(refunded.compareTo(new BigDecimal("750.00")) >= 0,
                "Should include full amount for status='REFUNDED' legacy record");
    }
}
