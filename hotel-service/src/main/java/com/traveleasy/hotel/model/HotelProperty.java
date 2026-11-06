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

    @Column(length = 100)
    private String propertyType; // Hotel, Resort, Villa, etc.

    private String city;
    private String address;
    private Integer starRating;

    @Column(length = 100)
    private String propertyClassification; // NOT_CLASSIFIED, 1_STAR, 5_STAR_DELUXE, OTHER, etc.

    @Column(length = 100)
    private String customClassification; // when classification is OTHER

    @Column(length = 50)
    private String propertySegment; // STANDARD, COMFORT, PREMIUM, LUXURY, ULTRA_LUXURY

    @Column(length = 150)
    private String brandName;

    @Column(length = 2000)
    private String description;
    private String amenities; // Swimming Pool, Free Wi-Fi, Restaurant, Gym, Spa
    private String imageUrl; // Primary/Cover image URL for backward compatibility

    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, INACTIVE, DRAFT

    @Builder.Default
    private Integer registrationStep = 1; // Current wizard step (1 to 5)
}
