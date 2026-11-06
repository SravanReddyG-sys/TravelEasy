package com.traveleasy.hotel.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hotel_booking_allocations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_booking_ref", columnNames = {"bookingRef"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelBookingAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
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
