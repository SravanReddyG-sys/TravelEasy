package com.traveleasy.hotel.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StayAvailabilityResponseDto {
    private Long hotelId;
    private Long roomId;
    private String roomTypeName;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer requestedRooms;
    private boolean available;
    private Integer availableRoomsAcrossStay; // min availability across all occupied nights
    private Integer totalNights;
    private Double minNightlyPrice;
    private Double maxNightlyPrice;
    private Double totalPrice; // (sum of nightly rates) * requestedRooms
    private List<NightlyRateDto> nightlyBreakdown;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NightlyRateDto {
        private LocalDate date;
        private Double price;
        private boolean isOverride;
        private Integer availableRooms;
    }
}
