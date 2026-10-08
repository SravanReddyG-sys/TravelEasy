package com.traveleasy.hotel.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long hotelId;
    private String roomTypeName; // Deluxe King Room, Executive Suite, Family Room
    private Integer maxOccupancy;
    private Double pricePerNight;
    private Integer totalRooms;
    private Integer availableRooms;
    private String amenities; // King Bed, AC, Mini Bar, City View
}
