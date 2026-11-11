package com.traveleasy.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Durable recovery task tracking refundable HOTEL booking cancellation refunds (WP-HC-01).
 * Preserves the exact authoritative policy refundAmount and immutable idempotencyKey
 * across retries, gateway timeouts, and application restarts.
 */
@Entity
@Table(name = "hotel_cancellation_refund_tasks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_hcrt_booking_id", columnNames = {"bookingId"}),
        @UniqueConstraint(name = "uk_hcrt_booking_ref", columnNames = {"bookingRef"}),
        @UniqueConstraint(name = "uk_hcrt_idempotency_key", columnNames = {"idempotencyKey"})
}, indexes = {
        @Index(name = "idx_hcrt_status_retry", columnList = "status, nextRetryAt"),
        @Index(name = "idx_hcrt_lease", columnList = "status, lockedUntil")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelCancellationRefundTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false, length = 120)
    private String bookingRef;

    @Column(nullable = false, length = 120)
    private String idempotencyKey;

    @Column(nullable = false)
    private Double refundAmount;

    @Column(nullable = false)
    private Double totalBookingAmount;

    @Column(nullable = false)
    private Double cancellationFee;

    @Column(nullable = false, length = 64)
    private String policyTier;

    @Column(length = 500)
    private String reason;

    /**
     * PENDING, PROCESSING, INITIATED, PROCESSED, FAILED_RETRYABLE, FAILED_FINAL
     */
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(length = 100)
    private String razorpayRefundId;

    @Column(nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer maxAttempts = 10;

    private Instant lastAttemptAt;

    private Instant nextRetryAt;

    private Instant completedAt;

    @Column(length = 80)
    private String lockedBy;

    private Instant lockedUntil;

    @Column(length = 2000)
    private String lastError;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
