package com.traveleasy.hotel.dto;

import com.traveleasy.hotel.model.HotelImage;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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

    // Contact Information (Step 3)
    private String propertyContactNumber;
    private String alternateContactNumber;
    private String propertyEmail;
    private String websiteUrl;

    // Business Registration (Step 3)
    private String registeredBusinessName;
    private String businessRegistrationType;
    private String otherBusinessRegistrationType;
    private String businessRegistrationNumber;
    private String registeredBusinessAddress;

    // GST Information (Step 3)
    private Boolean isGstRegistered;
    private String gstin;
    private String gstLegalName;
    private String gstState;

    // Property Manager Details (Step 3)
    private String propertyManagerName;
    private String propertyManagerDesignation;
    private String propertyManagerContactNumber;
    private String propertyManagerEmail;

    // Key Operational Details (Step 3)
    private String checkInTime;
    private String checkOutTime;
    private String receptionAvailability;
    private String receptionOpeningTime;
    private String receptionClosingTime;

    // Amenities (Step 4)
    private List<String> predefinedAmenities;
    private List<String> customAmenities;

    // Guest Policies (Step 4)
    private String childrenPolicy;
    private String petPolicy;
    private String smokingPolicy;
    private String extraBedPolicy;

    // Accepted Identification Proofs (Step 4)
    private List<String> acceptedIdentificationTypes;
    private String customIdentificationType;

    // Additional Rules (Step 4)
    private String additionalRules;

    // Registration & Activation Timestamps (Step 5)
    private LocalDateTime submittedAt;
    private LocalDateTime activatedAt;

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
                // Step 5 Timestamps
                .submittedAt(property.getSubmittedAt())
                .activatedAt(property.getActivatedAt())
                .images(imageDtos)
                .rooms(roomDtos)
                .build();
    }
}
