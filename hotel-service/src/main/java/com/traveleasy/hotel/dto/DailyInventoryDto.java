package com.traveleasy.hotel.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyInventoryDto {
    private LocalDate date;
    private Integer totalRooms;
    private Integer bookedRooms;
    private Integer heldRooms;
    private Integer blockedRooms;
    private Integer availableRooms;
    private Double effectivePrice;
}
