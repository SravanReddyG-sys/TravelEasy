package com.traveleasy.hotel.dto;

import com.traveleasy.hotel.model.HotelImage;
import com.traveleasy.hotel.model.HotelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelStep2ResponseDto {
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

    // Structured Address Fields (WP-05 Step 2)
    private String addressLine1;
    private String addressLine2;
    private String country;
    private String state;
    private String city;
    private String postalCode;
    private String landmark;
    private String address; // Legacy full address

    // Property Information (WP-05 Step 3)
    private String propertyContactNumber;
    private String alternateContactNumber;
    private String propertyEmail;
    private String websiteUrl;

    private String registeredBusinessName;
    private String businessRegistrationType;
    private String otherBusinessRegistrationType;
    private String businessRegistrationNumber;
    private String registeredBusinessAddress;

    private Boolean isGstRegistered;
    private String gstin;
    private String gstLegalName;
    private String gstState;

    private String propertyManagerName;
    private String propertyManagerDesignation;
    private String propertyManagerContactNumber;
    private String propertyManagerEmail;

    private String checkInTime;
    private String checkOutTime;
    private String receptionAvailability;
    private String receptionOpeningTime;
    private String receptionClosingTime;
    // Facilities & Policies (WP-05 Step 4)
    @Builder.Default
    private List<String> predefinedAmenities = new ArrayList<>();

    @Builder.Default
    private List<String> customAmenities = new ArrayList<>();

    private String childrenPolicy;
    private String petPolicy;
    private String smokingPolicy;
    private String extraBedPolicy;

    @Builder.Default
    private List<String> acceptedIdentificationTypes = new ArrayList<>();
    private String customIdentificationType;
    private String additionalRules;


    private String coverImageUrl;
    private String status;
    private Integer registrationStep;
    private List<HotelImageDto> images;

    public static HotelStep2ResponseDto fromEntity(HotelProperty property, List<HotelImage> imageEntities) {
        if (property == null) return null;
        List<HotelImageDto> imageDtos = imageEntities != null
                ? imageEntities.stream().map(HotelImageDto::fromEntity).toList()
                : List.of();

        String coverUrl = imageDtos.stream()
                .filter(HotelImageDto::isCover)
                .map(HotelImageDto::getImageUrl)
                .findFirst()
                .orElse(property.getImageUrl());

        return HotelStep2ResponseDto.builder()
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
                .addressLine1(property.getAddressLine1())
                .addressLine2(property.getAddressLine2())
                .country(property.getCountry())
                .state(property.getState())
                .city(property.getCity())
                .postalCode(property.getPostalCode())
                .landmark(property.getLandmark())
                .address(property.getAddress())
                .propertyContactNumber(property.getPropertyContactNumber())
                .alternateContactNumber(property.getAlternateContactNumber())
                .propertyEmail(property.getPropertyEmail())
                .websiteUrl(property.getWebsiteUrl())
                .registeredBusinessName(property.getRegisteredBusinessName())
                .businessRegistrationType(property.getBusinessRegistrationType())
                .otherBusinessRegistrationType(property.getOtherBusinessRegistrationType())
                .businessRegistrationNumber(property.getBusinessRegistrationNumber())
                .registeredBusinessAddress(property.getRegisteredBusinessAddress())
                .isGstRegistered(property.getIsGstRegistered())
                .gstin(property.getGstin())
                .gstLegalName(property.getGstLegalName())
                .gstState(property.getGstState())
                .propertyManagerName(property.getPropertyManagerName())
                .propertyManagerDesignation(property.getPropertyManagerDesignation())
                .propertyManagerContactNumber(property.getPropertyManagerContactNumber())
                .propertyManagerEmail(property.getPropertyManagerEmail())
                .checkInTime(property.getCheckInTime())
                .checkOutTime(property.getCheckOutTime())
                .receptionAvailability(property.getReceptionAvailability())
                .receptionOpeningTime(property.getReceptionOpeningTime())
                .receptionClosingTime(property.getReceptionClosingTime())
                .predefinedAmenities(HotelStep4ResponseDto.parseJsonList(property.getPredefinedAmenities()))
                .customAmenities(HotelStep4ResponseDto.parseJsonList(property.getCustomAmenities()))
                .childrenPolicy(property.getChildrenPolicy())
                .petPolicy(property.getPetPolicy())
                .smokingPolicy(property.getSmokingPolicy())
                .extraBedPolicy(property.getExtraBedPolicy())
                .acceptedIdentificationTypes(HotelStep4ResponseDto.parseJsonList(property.getAcceptedIdentificationTypes()))
                .customIdentificationType(property.getCustomIdentificationType())
                .additionalRules(property.getAdditionalRules())

                .coverImageUrl(coverUrl)
                .status(property.getStatus())
                .registrationStep(property.getRegistrationStep())
                .images(imageDtos)
                .build();
    }
}
