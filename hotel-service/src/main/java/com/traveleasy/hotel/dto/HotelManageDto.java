package com.traveleasy.hotel.dto;

import com.traveleasy.hotel.model.HotelImage;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelManageDto {
    private Long id;
    private Long managerId;
    private String managerName;
    private String name;
    private String propertyType;
    private String addressLine1;
    private String addressLine2;
    private String country;
    private String state;
    private String city;
    private String postalCode;
    private String landmark;
    private String address;
    private Integer starRating;
    private String propertyClassification;
    private String customClassification;
    private String propertySegment;
    private String brandName;
    private String description;
    private String amenities;
    private String imageUrl;
    private String status;
    private Integer registrationStep;
    private List<HotelImageDto> images;
    private List<RoomTypeDto> rooms;

    public static HotelManageDto fromEntity(HotelProperty property, List<RoomType> roomEntities) {
        return fromEntity(property, roomEntities, List.of());
    }

    public static HotelManageDto fromEntity(HotelProperty property, List<RoomType> roomEntities, List<HotelImage> imageEntities) {
        if (property == null) return null;
        List<RoomTypeDto> roomDtos = roomEntities != null
                ? roomEntities.stream().map(RoomTypeDto::fromEntity).toList()
                : List.of();

        List<HotelImageDto> imageDtos = imageEntities != null
                ? imageEntities.stream().map(HotelImageDto::fromEntity).toList()
                : List.of();

        return HotelManageDto.builder()
                .id(property.getId())
                .managerId(property.getManagerId())
                .managerName(property.getManagerName())
                .name(property.getName())
                .propertyType(property.getPropertyType())
                .addressLine1(property.getAddressLine1())
                .addressLine2(property.getAddressLine2())
                .country(property.getCountry())
                .state(property.getState())
                .city(property.getCity())
                .postalCode(property.getPostalCode())
                .landmark(property.getLandmark())
                .address(property.getAddress())
                .starRating(property.getStarRating())
                .propertyClassification(property.getPropertyClassification())
                .customClassification(property.getCustomClassification())
                .propertySegment(property.getPropertySegment())
                .brandName(property.getBrandName())
                .description(property.getDescription())
                .amenities(property.getAmenities())
                .imageUrl(property.getImageUrl())
                .status(property.getStatus())
                .registrationStep(property.getRegistrationStep())
                .images(imageDtos)
                .rooms(roomDtos)
                .build();
    }
}
