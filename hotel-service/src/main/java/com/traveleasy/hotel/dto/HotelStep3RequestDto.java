package com.traveleasy.hotel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotelStep3RequestDto {

    private Long draftId; // Draft ID to update

    // 1. Property Contact Details
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

    // 2. Business Registration Details
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

    // 3. GST Details
    @Builder.Default
    private Boolean isGstRegistered = false;

    @Size(max = 15, message = "GSTIN cannot exceed 15 characters")
    private String gstin;

    @Size(max = 255, message = "GST Legal Name cannot exceed 255 characters")
    private String gstLegalName;

    @Size(max = 100, message = "GST State cannot exceed 100 characters")
    private String gstState;

    // 4. Property Manager Details
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

    // 5. Operating Hours
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
}
