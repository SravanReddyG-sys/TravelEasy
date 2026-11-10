package com.traveleasy.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Persisted durable refund task for compensation tracking (WP-HB-05 Part B).
 * Guarantees that automated compensation refunds triggered by late payments,
 * expired holds, or inventory confirmation conflicts are resilient to Razorpay
 * outages, transient network errors, application restarts, and webhook retries.
 */
@Entity
@Table(name = "payment_refund_tasks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_refund_task_idempotency_key", columnNames = {"idempotencyKey"})
}, indexes = {
        @Index(name = "idx_refund_task_booking", columnList = "bookingRef"),
        @Index(name = "idx_refund_task_txn", columnList = "transactionRef"),
        @Index(name = "idx_refund_task_status_retry", columnList = "status, nextRetryAt"),
        @Index(name = "idx_refund_task_lease", columnList = "status, lockedUntil")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRefundTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String bookingRef;

    @Column(nullable = false)
    private String transactionRef;

    private String razorpayPaymentId;

    @Column(nullable = false)
    private Double amount;

    private String reason;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    /**
     * Lifecycle statuses:
     * - PENDING: Task created, awaiting immediate or background execution.
     * - PROCESSING: Leased by a worker or actively executing.
     * - INITIATED: Refund submitted to Razorpay, awaiting final webhook confirmation.
     * - FAILED_RETRYABLE: Transient failure encountered (e.g. gateway 5xx/timeout); retry scheduled.
     * - PROCESSED: Authoritatively confirmed and completed with provider refundId.
     * - FAILED_FINAL: Non-recoverable failure (e.g. max attempts exhausted or permanent rejection).
     */
    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    private String razorpayRefundId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant lastAttemptAt;

    private Instant nextRetryAt;

    @Builder.Default
    private Integer attemptCount = 0;

    @Builder.Default
    private Integer maxAttempts = 5;

    @Column(length = 2000)
    private String lastError;

    private String lockedBy;

    private Instant lockedUntil;
}
