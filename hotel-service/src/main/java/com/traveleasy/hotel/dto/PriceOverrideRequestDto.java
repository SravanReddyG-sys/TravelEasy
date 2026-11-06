package com.traveleasy.hotel.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceOverrideRequestDto {
    private LocalDate overrideDate;
    private Double pricePerNight;
}
