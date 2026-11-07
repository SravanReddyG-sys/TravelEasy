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
public class RoomTypeRequestDto {
    @NotBlank(message = "Room type name is required")
    @Size(max = 100, message = "Room category name cannot exceed 100 characters")
    private String roomTypeName;

    @Min(value = 1, message = "Max occupancy must be at least 1 person")
    @Max(value = 20, message = "Max occupancy cannot exceed 20 people")
    private Integer maxOccupancy;

    @DecimalMin(value = "1.0", message = "Price per night must be at least 1.0")
    private Double pricePerNight;

    @NotNull(message = "Total rooms is required")
    @Min(value = 1, message = "Total rooms must be at least 1")
    private Integer totalRooms;

    @Size(max = 1000, message = "Amenities list cannot exceed 1000 characters")
    private String amenities;

    @Size(max = 500, message = "Image URL cannot exceed 500 characters")
    private String imageUrl;
}
