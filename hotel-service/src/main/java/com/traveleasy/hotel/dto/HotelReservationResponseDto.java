package com.traveleasy.hotel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Authoritative response payload for a consolidated hotel reservation (WP-HB-04).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelReservationResponseDto {

    private String bookingRef;
    private Long hotelId;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer totalNights;
    private String status; // HELD, CONFIRMED, RELEASED
    private Double totalPrice;
    private LocalDateTime expiresAt;
    private Boolean idempotent;
    private List<AllocationDetailDto> allocations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AllocationDetailDto {
        private Long allocationId;
        private Long roomId;
        private String roomTypeName;
        private Integer quantity;
        private Double allocationTotal;
        private List<NightlyPriceDto> nightlyPriceBreakdown;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NightlyPriceDto {
        private LocalDate date;
        private Double rate;
        private Boolean isOverride;
    }
}
