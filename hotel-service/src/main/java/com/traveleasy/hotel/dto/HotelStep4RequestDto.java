package com.traveleasy.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class HotelStep4RequestDto {

    private Long draftId;

    // 1. Property Facilities & Amenities
    @Builder.Default
    private List<String> predefinedAmenities = new ArrayList<>();

    @Builder.Default
    private List<String> customAmenities = new ArrayList<>();

    // 2. Guest Policies
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

    // 3. Additional Property Rules
    @Size(max = 1000, message = "Additional property rules must not exceed 1000 characters")
    private String additionalRules;
}
