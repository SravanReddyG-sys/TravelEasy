package com.traveleasy.hotel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BasePriceUpdateRequestDto {

    @NotNull(message = "Base price is mandatory")
    @DecimalMin(value = "0.01", message = "Base price must be positive and greater than zero")
    private Double basePrice;
}
