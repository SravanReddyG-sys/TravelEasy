package com.traveleasy.hotel.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceCalendarEntryDto {
    private LocalDate date;
    private Double basePrice;
    private Double overridePrice;
    private Double effectivePrice;
    private boolean isOverride;
}
