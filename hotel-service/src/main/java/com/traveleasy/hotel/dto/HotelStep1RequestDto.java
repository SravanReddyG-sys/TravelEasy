package com.traveleasy.hotel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotelStep1RequestDto {

    private Long id; // Optional draft ID for updating an existing draft

    @NotBlank(message = "Property name is required")
    @Size(max = 255, message = "Property name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "Property type is required")
    @Size(max = 100, message = "Property type cannot exceed 100 characters")
    private String propertyType;

    @Size(max = 100, message = "Property classification cannot exceed 100 characters")
    private String propertyClassification; // NOT_CLASSIFIED, 1_STAR, 2_STAR, 3_STAR, 4_STAR, 5_STAR, 5_STAR_DELUXE, OTHER

    @Size(max = 100, message = "Custom classification cannot exceed 100 characters")
    private String customClassification;

    @NotBlank(message = "Property segment is required")
    @Size(max = 50, message = "Property segment cannot exceed 50 characters")
    private String propertySegment; // STANDARD, COMFORT, PREMIUM, LUXURY, ULTRA_LUXURY

    @Size(max = 150, message = "Brand name cannot exceed 150 characters")
    private String brandName;

    @NotBlank(message = "Short description is required")
    @Size(max = 300, message = "Short description cannot exceed 300 characters")
    private String description;

    @NotNull(message = "Property images are required")
    @Size(min = 1, max = 4, message = "Between 1 and 4 images must be provided")
    @Valid
    private List<HotelImageDto> images;
}
