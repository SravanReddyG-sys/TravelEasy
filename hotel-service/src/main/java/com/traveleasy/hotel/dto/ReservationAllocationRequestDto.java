package com.traveleasy.hotel.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for a single room type allocation within a consolidated reservation (WP-HB-04).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationAllocationRequestDto {

    @NotNull(message = "Room ID is mandatory")
    private Long roomId;

    @NotNull(message = "Quantity is mandatory")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
}
