package com.traveleasy.hotel.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hotel_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hotelId;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    private String originalFileName;

    @Builder.Default
    private boolean isCover = false;

    @Builder.Default
    private int displayOrder = 0;
}
