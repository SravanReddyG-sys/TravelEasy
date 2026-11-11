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
 * Durable outbox/recovery task tracking hotel room inventory release
 * in hotel-service when a HOTEL booking is cancelled (WP-HC-01).
 */
@Entity
@Table(name = "hotel_inventory_release_tasks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_hirt_booking_id", columnNames = {"bookingId"}),
        @UniqueConstraint(name = "uk_hirt_booking_ref", columnNames = {"bookingRef"})
}, indexes = {
        @Index(name = "idx_hirt_status_retry", columnList = "status, nextRetryAt"),
        @Index(name = "idx_hirt_lease", columnList = "status, lockedUntil")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelInventoryReleaseTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false, length = 120)
    private String bookingRef;

    private Long hotelId;

    private Long serviceId;

    @Column(nullable = false)
    @Builder.Default
    private boolean dateWiseBooking = true;

    @Column(columnDefinition = "TEXT")
    private String detailsJson;

    /**
     * PENDING, PROCESSING, RELEASED, FAILED_RETRYABLE, FAILED_FINAL
     */
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

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
