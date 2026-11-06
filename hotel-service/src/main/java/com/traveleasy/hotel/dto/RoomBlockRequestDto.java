package com.traveleasy.hotel.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomBlockRequestDto {
    private Integer blockedCount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason; // MAINTENANCE, RENOVATION, OPERATIONAL, OTHER
    private String notes;
}
