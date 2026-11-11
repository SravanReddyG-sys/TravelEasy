package com.traveleasy.payment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "payment_webhook_events", indexes = {
        @Index(name = "idx_webhook_event_id", columnList = "webhookEventId", unique = true),
        @Index(name = "idx_webhook_status", columnList = "status"),
        @Index(name = "idx_webhook_recovery", columnList = "status, lockedUntil")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String webhookEventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "RECEIVED"; // RECEIVED, PROCESSING, PROCESSED, FAILED

    @Builder.Default
    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    private Instant processedAt;

    @Column(length = 1000)
    private String lastError;

    @Column(length = 100)
    private String lockedBy;

    private Instant lockedUntil;

    private Instant lastAttemptAt;

    @Builder.Default
    @Column(nullable = false)
    private int attemptCount = 0;
}
