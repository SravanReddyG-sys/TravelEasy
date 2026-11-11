package com.traveleasy.hotel.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parent reservation aggregate for multi-allocation hotel bookings (WP-HB-04).
 * Owns and coordinates all child room allocations (HotelBookingAllocation)
 * under a single authoritative booking reference.
 */
@Entity
@Table(name = "hotel_reservations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reservation_booking_ref", columnNames = {"bookingRef"})
}, indexes = {
        @Index(name = "idx_reservation_hotel_dates", columnList = "hotelId, checkInDate, checkOutDate"),
        @Index(name = "idx_reservation_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String bookingRef;

    @Column(nullable = false)
    private Long hotelId;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Column(nullable = false)
    @Builder.Default
    private String status = "HELD"; // HELD, CONFIRMED, RELEASED

    @Column(length = 64)
    private String requestHash;

    /**
     * Authoritative reservation expiry deadline established by Booking Service (WP-HB-05).
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    private Double totalPrice;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<HotelBookingAllocation> allocations = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void addAllocation(HotelBookingAllocation allocation) {
        if (allocations == null) {
            allocations = new ArrayList<>();
        }
        allocations.add(allocation);
        allocation.setReservation(this);
    }
}
