package com.traveleasy.hotel.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomBlockResponseDto {
    private Long id;
    private Long hotelId;
    private Long roomId;
    private String roomTypeName;
    private Long managerId;
    private Integer blockedCount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private String notes;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;
}
