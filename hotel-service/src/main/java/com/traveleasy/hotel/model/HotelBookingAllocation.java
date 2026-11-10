package com.traveleasy.hotel.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Child allocation record representing a specific room type quantity (WP-HB-04).
 * Associated with parent HotelReservation aggregate.
 * Retains bookingRef as a nullable, non-unique compatibility field.
 */
@Entity
@Table(name = "hotel_booking_allocations", indexes = {
        @Index(name = "idx_alloc_reservation_id", columnList = "reservation_id"),
        @Index(name = "idx_alloc_room_dates", columnList = "roomId, checkInDate, checkOutDate"),
        @Index(name = "idx_alloc_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelBookingAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private HotelReservation reservation;

    /**
     * Retained as a nullable, non-unique compatibility field.
     * Derived from parent HotelReservation.bookingRef when associated with a parent.
     */
    @Column(nullable = true)
    private String bookingRef;

    @Column(nullable = false)
    private Long hotelId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Column(nullable = false)
    private Integer roomCount;

    @Column(nullable = false)
    @Builder.Default
    private String status = "HELD"; // HELD, CONFIRMED, RELEASED

    private Double totalPrice;

    @Column(columnDefinition = "TEXT")
    private String nightlyRatesJson;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
