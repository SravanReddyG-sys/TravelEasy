package com.traveleasy.hotel.dto;

import com.traveleasy.hotel.model.HotelImage;
import com.traveleasy.hotel.model.HotelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelStep5ResponseDto {

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
    // 1. Contact
    private String propertyContactNumber;
    private String alternateContactNumber;
    private String propertyEmail;
    private String websiteUrl;

    // 2. Business Registration
    private String registeredBusinessName;
    private String businessRegistrationType;
    private String otherBusinessRegistrationType;
    private String businessRegistrationNumber;
    private String registeredBusinessAddress;

    // 3. GST
    private Boolean isGstRegistered;
    private String gstin;
    private String gstLegalName;
    private String gstState;

    // 4. Property Manager
    private String propertyManagerName;
    private String propertyManagerDesignation;
    private String propertyManagerContactNumber;
    private String propertyManagerEmail;

    // 5. Operating Hours
    private String checkInTime;
    private String checkOutTime;
    private String receptionAvailability;
    private String receptionOpeningTime;
    private String receptionClosingTime;

    // Facilities & Policies (WP-05 Step 4)
    // 1. Facilities & Amenities
    @Builder.Default
    private List<String> predefinedAmenities = new ArrayList<>();

    @Builder.Default
    private List<String> customAmenities = new ArrayList<>();

    // 2. Guest Policies
    private String childrenPolicy;
    private String petPolicy;
    private String smokingPolicy;
    private String extraBedPolicy;

    @Builder.Default
    private List<String> acceptedIdentificationTypes = new ArrayList<>();
    private String customIdentificationType;

    // 3. Additional Property Rules
    private String additionalRules;

    // Legacy amenities string
    private String amenities;

    // Lifecycle & Wizard State
    private String status;
    private Integer registrationStep;
    private String coverImageUrl;

    @Builder.Default
    private List<HotelImageDto> images = new ArrayList<>();

    // WP-05 Step 5 Submission Details
    private LocalDateTime submittedAt;
    private LocalDateTime activatedAt;
    private String message;

    public static HotelStep5ResponseDto fromEntity(HotelProperty property, List<HotelImage> imageEntities, String message) {
        if (property == null) return null;
        List<HotelImageDto> imageDtos = imageEntities != null
                ? imageEntities.stream().map(HotelImageDto::fromEntity).toList()
                : List.of();

        String coverUrl = imageDtos.stream()
                .filter(HotelImageDto::isCover)
                .map(HotelImageDto::getImageUrl)
                .findFirst()
                .orElse(property.getImageUrl());

        return HotelStep5ResponseDto.builder()
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
                // Step 3
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
                // Step 4
                .predefinedAmenities(HotelStep4ResponseDto.parseJsonList(property.getPredefinedAmenities()))
                .customAmenities(HotelStep4ResponseDto.parseJsonList(property.getCustomAmenities()))
                .childrenPolicy(property.getChildrenPolicy())
                .petPolicy(property.getPetPolicy())
                .smokingPolicy(property.getSmokingPolicy())
                .extraBedPolicy(property.getExtraBedPolicy())
                .acceptedIdentificationTypes(HotelStep4ResponseDto.parseJsonList(property.getAcceptedIdentificationTypes()))
                .customIdentificationType(property.getCustomIdentificationType())
                .additionalRules(property.getAdditionalRules())
                .amenities(property.getAmenities())
                // Lifecycle & Step 5
                .status(property.getStatus())
                .registrationStep(property.getRegistrationStep())
                .coverImageUrl(coverUrl)
                .images(imageDtos)
                .submittedAt(property.getSubmittedAt())
                .activatedAt(property.getActivatedAt())
                .message(message != null ? message : "Property successfully registered and activated.")
                .build();
    }
}
