package com.traveleasy.hotel.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "room_price_overrides", uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_override_date", columnNames = {"roomId", "overrideDate"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomPriceOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hotelId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private LocalDate overrideDate;

    @Column(nullable = false)
    private Double pricePerNight;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
