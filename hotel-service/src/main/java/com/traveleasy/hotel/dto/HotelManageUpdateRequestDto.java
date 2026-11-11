package com.traveleasy.hotel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated DTO for complete full-page property editing (PUT /api/hotels/{hotelId}/manage).
 * Encapsulates all editable fields across Steps 1-4 of the Property Management Editor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotelManageUpdateRequestDto {

    // =========================================================================
    // STEP 1: Basic Property Details & Images
    // =========================================================================
    @NotBlank(message = "Property name is required")
    @Size(max = 255, message = "Property name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "Property type is required")
    @Size(max = 100, message = "Property type cannot exceed 100 characters")
    private String propertyType;

    @Size(max = 100, message = "Property classification cannot exceed 100 characters")
    private String propertyClassification;

    @Size(max = 100, message = "Custom classification cannot exceed 100 characters")
    private String customClassification;

    @NotBlank(message = "Property segment is required")
    @Size(max = 50, message = "Property segment cannot exceed 50 characters")
    private String propertySegment;

    @Size(max = 150, message = "Brand name cannot exceed 150 characters")
    private String brandName;

    @NotBlank(message = "Short description is required")
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @NotNull(message = "Property images are required")
    @Size(min = 1, max = 4, message = "Between 1 and 4 images must be provided")
    @Valid
    @Builder.Default
    private List<HotelImageDto> images = new ArrayList<>();

    // =========================================================================
    // STEP 2: Location & Address
    // =========================================================================
    @NotBlank(message = "Address Line 1 is required")
    @Size(max = 255, message = "Address Line 1 cannot exceed 255 characters")
    private String addressLine1;

    @Size(max = 255, message = "Address Line 2 cannot exceed 255 characters")
    private String addressLine2;

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country cannot exceed 100 characters")
    private String country;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String state;

    @NotBlank(message = "City is required")
    @Size(max = 255, message = "City cannot exceed 255 characters")
    private String city;

    @NotBlank(message = "Postal Code is required")
    @Size(max = 20, message = "Postal Code cannot exceed 20 characters")
    private String postalCode;

    @Size(max = 255, message = "Landmark cannot exceed 255 characters")
    private String landmark;

    // =========================================================================
    // STEP 3: Property Information & Business Details
    // =========================================================================
    @NotBlank(message = "Property Contact Number is required")
    @Size(max = 30, message = "Property Contact Number cannot exceed 30 characters")
    private String propertyContactNumber;

    @Size(max = 30, message = "Alternate Contact Number cannot exceed 30 characters")
    private String alternateContactNumber;

    @NotBlank(message = "Property Email Address is required")
    @Email(message = "Property Email Address must be a valid email format")
    @Size(max = 100, message = "Property Email Address cannot exceed 100 characters")
    private String propertyEmail;

    @Size(max = 255, message = "Website URL cannot exceed 255 characters")
    private String websiteUrl;

    @NotBlank(message = "Registered Business Name is required")
    @Size(max = 255, message = "Registered Business Name cannot exceed 255 characters")
    private String registeredBusinessName;

    @NotBlank(message = "Business Registration Type is required")
    @Size(max = 100, message = "Business Registration Type cannot exceed 100 characters")
    private String businessRegistrationType;

    @Size(max = 100, message = "Custom Business Registration Type cannot exceed 100 characters")
    private String otherBusinessRegistrationType;

    @NotBlank(message = "Business Registration Number is required")
    @Size(max = 100, message = "Business Registration Number cannot exceed 100 characters")
    private String businessRegistrationNumber;

    private String registeredBusinessAddress;

    @Builder.Default
    private Boolean isGstRegistered = false;

    @Size(max = 15, message = "GSTIN cannot exceed 15 characters")
    private String gstin;

    @Size(max = 255, message = "GST Legal Name cannot exceed 255 characters")
    private String gstLegalName;

    @Size(max = 100, message = "GST State cannot exceed 100 characters")
    private String gstState;

    @NotBlank(message = "Manager Full Name is required")
    @Size(max = 255, message = "Manager Full Name cannot exceed 255 characters")
    private String propertyManagerName;

    @NotBlank(message = "Manager Designation is required")
    @Size(max = 100, message = "Manager Designation cannot exceed 100 characters")
    private String propertyManagerDesignation;

    @NotBlank(message = "Manager Contact Number is required")
    @Size(max = 30, message = "Manager Contact Number cannot exceed 30 characters")
    private String propertyManagerContactNumber;

    @NotBlank(message = "Manager Email Address is required")
    @Email(message = "Manager Email Address must be a valid email format")
    @Size(max = 100, message = "Manager Email Address cannot exceed 100 characters")
    private String propertyManagerEmail;

    @NotBlank(message = "Check-in Time is required")
    @Size(max = 20, message = "Check-in Time cannot exceed 20 characters")
    private String checkInTime;

    @NotBlank(message = "Check-out Time is required")
    @Size(max = 20, message = "Check-out Time cannot exceed 20 characters")
    private String checkOutTime;

    @NotBlank(message = "Reception Availability is required")
    @Size(max = 50, message = "Reception Availability cannot exceed 50 characters")
    private String receptionAvailability;

    @Size(max = 20, message = "Reception Opening Time cannot exceed 20 characters")
    private String receptionOpeningTime;

    @Size(max = 20, message = "Reception Closing Time cannot exceed 20 characters")
    private String receptionClosingTime;

    // =========================================================================
    // STEP 4: Facilities & Policies
    // =========================================================================
    @Builder.Default
    private List<String> predefinedAmenities = new ArrayList<>();

    @Builder.Default
    private List<String> customAmenities = new ArrayList<>();

    @NotBlank(message = "Children policy is required")
    private String childrenPolicy;

    @NotBlank(message = "Pet policy is required")
    private String petPolicy;

    @NotBlank(message = "Smoking policy is required")
    private String smokingPolicy;

    @NotBlank(message = "Extra bed policy is required")
    private String extraBedPolicy;

    @NotEmpty(message = "At least one accepted identification type is required")
    @Builder.Default
    private List<String> acceptedIdentificationTypes = new ArrayList<>();

    @Size(max = 150, message = "Custom identification type must not exceed 150 characters")
    private String customIdentificationType;

    @Size(max = 1000, message = "Additional property rules must not exceed 1000 characters")
    private String additionalRules;
}
