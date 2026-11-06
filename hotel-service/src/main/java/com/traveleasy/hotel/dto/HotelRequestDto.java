package com.traveleasy.hotel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotelRequestDto {
    @NotBlank(message = "Hotel name is required")
    @Size(max = 255, message = "Hotel name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @NotBlank(message = "Address is required")
    @Size(max = 500, message = "Address cannot exceed 500 characters")
    private String address;

    @Min(value = 1, message = "Star rating must be at least 1")
    @Max(value = 5, message = "Star rating cannot exceed 5")
    private Integer starRating;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @Size(max = 500, message = "Amenities list cannot exceed 500 characters")
    private String amenities;

    @Size(max = 1000, message = "Image URL cannot exceed 1000 characters")
    private String imageUrl;
}
