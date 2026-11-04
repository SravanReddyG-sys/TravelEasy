package com.traveleasy.payment.repository;

import com.traveleasy.payment.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByBookingRef(String bookingRef);
    Optional<PaymentTransaction> findByTransactionRef(String transactionRef);
    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);
    Optional<PaymentTransaction> findByRazorpayPaymentId(String razorpayPaymentId);
    Optional<PaymentTransaction> findByRefundId(String refundId);
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * WP-04: Authoritative database aggregation for successful captured collections.
     * Counts only transactions where status is SUCCESS, REFUND_INITIATED, or REFUNDED in the given currency.
     * Returns exact BigDecimal with 2 decimal places directly from database engine.
     */
    @Query(value = "SELECT CAST(COALESCE(SUM(p.amount), 0) AS DECIMAL(15,2)) " +
                   "FROM payment_transactions p " +
                   "WHERE p.status IN ('SUCCESS', 'REFUND_INITIATED', 'REFUNDED') " +
                   "  AND p.currency = :currency", nativeQuery = true)
    BigDecimal sumSuccessfulCapturedCollections(@Param("currency") String currency);

    /**
     * WP-04: Authoritative database aggregation for processed refunds.
     * Deducts only refunds where refundStatus = 'PROCESSED' in the given currency.
     * Fallback policy: Uses explicit refund_amount; if missing, falls back to amount ONLY if
     * status is 'REFUNDED' (legacy full refund signal); otherwise defaults to 0.00.
     * Returns exact BigDecimal with 2 decimal places directly from database engine.
     */
    @Query(value = "SELECT CAST(COALESCE(SUM(COALESCE(p.refund_amount, CASE WHEN p.status = 'REFUNDED' THEN p.amount ELSE 0.0 END)), 0) AS DECIMAL(15,2)) " +
                   "FROM payment_transactions p " +
                   "WHERE p.refund_status = 'PROCESSED' " +
                   "  AND p.currency = :currency", nativeQuery = true)
    BigDecimal sumProcessedRefunds(@Param("currency") String currency);

    /**
     * WP-04: Daily captured collections native aggregation grouped by capture date.
     * Attributed strictly to actual payment capture date (timestamp).
     */
    @Query(value = "SELECT CAST(p.timestamp AS DATE) AS dt, " +
                   "       CAST(COALESCE(SUM(p.amount), 0) AS DECIMAL(15,2)) AS capturedAmount " +
                   "FROM payment_transactions p " +
                   "WHERE p.status IN ('SUCCESS', 'REFUND_INITIATED', 'REFUNDED') " +
                   "  AND p.currency = :currency " +
                   "  AND p.timestamp >= :startDateTime " +
                   "  AND p.timestamp <= :endDateTime " +
                   "GROUP BY CAST(p.timestamp AS DATE) " +
                   "ORDER BY dt ASC", nativeQuery = true)
    List<Object[]> countDailyCapturedNative(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("currency") String currency);

    /**
     * WP-04: Daily processed refunds native aggregation grouped by refund execution date.
     * Attributed strictly to actual refund processing date (refunded_at).
     * Policy: Excludes records where refunded_at IS NULL to avoid attributing refunds to false dates.
     */
    @Query(value = "SELECT CAST(p.refunded_at AS DATE) AS dt, " +
                   "       CAST(COALESCE(SUM(COALESCE(p.refund_amount, CASE WHEN p.status = 'REFUNDED' THEN p.amount ELSE 0.0 END)), 0) AS DECIMAL(15,2)) AS refundedAmount " +
                   "FROM payment_transactions p " +
                   "WHERE p.refund_status = 'PROCESSED' " +
                   "  AND p.currency = :currency " +
                   "  AND p.refunded_at IS NOT NULL " +
                   "  AND p.refunded_at >= :startDateTime " +
                   "  AND p.refunded_at <= :endDateTime " +
                   "GROUP BY CAST(p.refunded_at AS DATE) " +
                   "ORDER BY dt ASC", nativeQuery = true)
    List<Object[]> countDailyRefundedNative(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("currency") String currency);
}
