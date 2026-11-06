package com.traveleasy.hotel.dto;

import com.traveleasy.hotel.model.HotelImage;
import com.traveleasy.hotel.model.HotelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelStep1ResponseDto {
    private Long id;
    private Long managerId;
    private String managerName;
    private String name;
    private String propertyType;
    private String propertyClassification;
    private String customClassification;
    private Integer starRating;
    private String propertySegment;
    private String brandName;
    private String description;
    private String coverImageUrl;
    private String status;
    private Integer registrationStep;
    private List<HotelImageDto> images;

    public static HotelStep1ResponseDto fromEntity(HotelProperty property, List<HotelImage> imageEntities) {
        if (property == null) return null;
        List<HotelImageDto> imageDtos = imageEntities != null
                ? imageEntities.stream().map(HotelImageDto::fromEntity).toList()
                : List.of();

        String coverUrl = imageDtos.stream()
                .filter(HotelImageDto::isCover)
                .map(HotelImageDto::getImageUrl)
                .findFirst()
                .orElse(property.getImageUrl());

        return HotelStep1ResponseDto.builder()
                .id(property.getId())
                .managerId(property.getManagerId())
                .managerName(property.getManagerName())
                .name(property.getName())
                .propertyType(property.getPropertyType())
                .propertyClassification(property.getPropertyClassification())
                .customClassification(property.getCustomClassification())
                .starRating(property.getStarRating())
                .propertySegment(property.getPropertySegment())
                .brandName(property.getBrandName())
                .description(property.getDescription())
                .coverImageUrl(coverUrl)
                .status(property.getStatus())
                .registrationStep(property.getRegistrationStep())
                .images(imageDtos)
                .build();
    }
}
