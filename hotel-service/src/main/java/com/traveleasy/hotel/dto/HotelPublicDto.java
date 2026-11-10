package com.traveleasy.hotel.dto;

import com.traveleasy.hotel.model.HotelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public Data Transfer Object for Hotel Properties.
 * Exposes only traveller-facing attributes and strictly omits internal fields
 * such as managerId, managerName, and operational status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelPublicDto {
    private Long id;
    private String name;
    private String city;
    private String address;
    private Integer starRating;
    private String description;
    private String amenities;
    private String imageUrl;
    private Double minPrice;
    private String propertyType;

    public static HotelPublicDto fromEntity(HotelProperty property) {
        if (property == null) {
            return null;
        }
        return HotelPublicDto.builder()
                .id(property.getId())
                .name(property.getName())
                .city(property.getCity())
                .address(property.getAddress())
                .starRating(property.getStarRating())
                .description(property.getDescription())
                .amenities(property.getAmenities())
                .imageUrl(property.getImageUrl())
                .propertyType(property.getPropertyType())
                .build();
    }

    public static HotelPublicDto fromEntity(HotelProperty property, Double minPrice) {
        if (property == null) {
            return null;
        }
        return HotelPublicDto.builder()
                .id(property.getId())
                .name(property.getName())
                .city(property.getCity())
                .address(property.getAddress())
                .starRating(property.getStarRating())
                .description(property.getDescription())
                .amenities(property.getAmenities())
                .imageUrl(property.getImageUrl())
                .propertyType(property.getPropertyType())
                .minPrice(minPrice)
                .build();
    }
}
