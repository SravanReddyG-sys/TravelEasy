package com.traveleasy.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "booking_outbox_events", indexes = {
        @Index(name = "idx_outbox_status_retry", columnList = "status, nextRetryAt"),
        @Index(name = "idx_outbox_locked_until", columnList = "lockedUntil")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "PENDING"; // PENDING, IN_FLIGHT, PUBLISHED, FAILED

    @Builder.Default
    @Column(nullable = false)
    private int retryCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private int maxRetries = 5;

    private Instant nextRetryAt;

    private Instant lastAttemptAt;

    @Column(length = 64)
    private String lockedBy;

    private Instant lockedUntil;

    @Column(length = 1000)
    private String lastError;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant publishedAt;
}
