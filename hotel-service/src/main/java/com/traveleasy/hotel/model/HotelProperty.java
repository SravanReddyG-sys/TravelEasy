package com.traveleasy.hotel.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hotel_properties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelProperty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long managerId;
    private String managerName;
    private String name;
    private String city;
    private String address;
    private Integer starRating;
    @Column(length = 2000)
    private String description;
    private String amenities; // Swimming Pool, Free Wi-Fi, Restaurant, Gym, Spa
    private String imageUrl;
}
