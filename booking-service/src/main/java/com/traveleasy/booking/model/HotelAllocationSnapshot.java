package com.traveleasy.booking.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Relational snapshot of an individual room-type allocation within a consolidated hotel reservation (WP-HB-05).
 * Stores authoritative historical nightly pricing and provides schema readiness for WP-HB-06 guest allocation.
 */
@Entity
@Table(name = "hotel_allocation_snapshots", indexes = {
        @Index(name = "idx_has_reservation_id", columnList = "reservation_snapshot_id"),
        @Index(name = "idx_has_room_id", columnList = "roomId"),
        @Index(name = "idx_has_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelAllocationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_snapshot_id", nullable = false)
    @JsonBackReference
    private HotelReservationSnapshot reservationSnapshot;

    @Column(nullable = false)
    private Long roomId;

    private String roomTypeName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Double allocationTotal;

    @Column(columnDefinition = "TEXT")
    private String nightlyRatesJson;

    private LocalDate checkInDate;

    private LocalDate checkOutDate;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "HELD"; // HELD, CONFIRMED, RELEASED, CANCELLED, EXPIRED

    /**
     * WP-HB-06 compatibility column: stores structured per-room individual guest and child age allocations
     * without requiring an architectural schema migration for guest allocation.
     */
    @Column(columnDefinition = "TEXT")
    private String guestAllocationJson;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
