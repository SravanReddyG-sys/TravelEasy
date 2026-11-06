package com.traveleasy.hotel.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceRangeOverrideRequestDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private Double pricePerNight;
}
