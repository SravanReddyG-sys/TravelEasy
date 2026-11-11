package com.traveleasy.hotel.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DateWiseReserveRequestDto {
    private String bookingRef;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    @Builder.Default
    private Integer count = 1;
}
