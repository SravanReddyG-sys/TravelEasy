package com.traveleasy.hotel.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelInventorySummaryDto {
    private Long hotelId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalPhysicalRoomNights;
    private Integer occupiedRoomNights;
    private Integer heldRoomNights;
    private Integer blockedRoomNights;
    private Integer availableRoomNights;
    private Integer applicableRoomNightCapacity;
    private Double occupancyRate;
    private Integer activeRoomTypesCount;
    private Integer activeRoomBlocksCount;
    private Integer lowAvailabilityRoomsCount;
    private Integer exhaustedRoomsCount;
    private List<LowAvailabilityRoomItem> lowAvailabilityRooms;
    private List<DailySummaryItem> dailySummaries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LowAvailabilityRoomItem {
        private Long roomId;
        private String roomTypeName;
        private Integer totalRooms;
        private Integer minAvailableRooms;
        private boolean exhausted;
        private Integer lowAvailabilityDatesCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailySummaryItem {
        private LocalDate date;
        private Integer totalRooms;
        private Integer bookedRooms;
        private Integer heldRooms;
        private Integer blockedRooms;
        private Integer availableRooms;
        private Double occupancyRate;
    }
}
