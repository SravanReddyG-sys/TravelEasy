package com.traveleasy.hotel.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DateWiseReleaseRequestDto {
    private String bookingRef;
    private Integer count;
}
