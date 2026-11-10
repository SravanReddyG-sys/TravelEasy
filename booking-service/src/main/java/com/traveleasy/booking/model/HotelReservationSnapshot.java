package com.traveleasy.booking.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Relational snapshot of a consolidated hotel reservation (WP-HB-05).
 * Replaces reliance on the 4000-character detailsJson column for multi-room hotel bookings.
 */
@Entity
@Table(name = "hotel_reservation_snapshots", indexes = {
        @Index(name = "idx_hrs_booking_id", columnList = "bookingId", unique = true),
        @Index(name = "idx_hrs_booking_ref", columnList = "bookingRef"),
        @Index(name = "idx_hrs_hotel_id", columnList = "hotelId"),
        @Index(name = "idx_hrs_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelReservationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long bookingId;

    @Column(nullable = false)
    private String bookingRef;

    @Column(nullable = false)
    private Long hotelId;

    private String hotelName;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    private Integer totalNights;

    @Column(nullable = false)
    private Double totalAmount;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String currency = "INR";

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "HELD"; // HELD, CONFIRMED, RELEASED, CANCELLED, EXPIRED

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "reservationSnapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    @Builder.Default
    private List<HotelAllocationSnapshot> allocations = new ArrayList<>();

    public void addAllocation(HotelAllocationSnapshot allocation) {
        if (allocations == null) {
            allocations = new ArrayList<>();
        }
        allocations.add(allocation);
        allocation.setReservationSnapshot(this);
    }
}
