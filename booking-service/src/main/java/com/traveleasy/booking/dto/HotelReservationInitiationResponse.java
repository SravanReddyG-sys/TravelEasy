package com.traveleasy.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Authoritative response returned when a consolidated hotel reservation is initiated (WP-HB-05).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelReservationInitiationResponse {

    private Long bookingId;
    private String bookingRef;
    private String status;
    private Double totalAmount;
    private String currency;
    private LocalDateTime expiresAt;
    private Long hotelId;
    private String hotelName;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer totalNights;
    private Boolean idempotent;
    private List<AllocationSnapshotDto> allocations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AllocationSnapshotDto {
        private Long allocationId;
        private Long roomId;
        private String roomTypeName;
        private Integer quantity;
        private Double allocationTotal;
        private String nightlyRatesJson;
        private String status;
    }
}
