package com.traveleasy.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detailed representation of a hotel reservation for retrieval (WP-HB-06.2).
 * Returns complete parent booking, hotel, stay, primary guest, physical-room guest allocations,
 * and immutable pricing snapshot details.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelReservationDetailResponse {

    private Long bookingId;
    private String bookingRef;
    private String bookingStatus;
    private HotelSummaryDto hotel;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer totalNights;
    private PrimaryGuestDto primaryGuest;
    private List<AllocationDetailDto> allocations;
    private Double totalAmount;
    private String currency;
    private String paymentStatus;
    private LocalDateTime expiresAt;
    private LocalDateTime bookingDate;
    private CancellationDetailDto cancellation;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HotelSummaryDto {
        private Long hotelId;
        private String hotelName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AllocationDetailDto {
        private Long allocationId;
        private Long roomId;
        private String roomTypeName;
        private Integer quantity;
        private List<RoomGuestAllocationDto> rooms;
        private AllocationPricingDto pricing;
        private String status;
        private String guestAllocationJson; // Retained for historical backward compatibility
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AllocationPricingDto {
        private Double allocationTotal;
        private String nightlyRatesJson;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CancellationDetailDto {
        private Boolean cancelled;
        private LocalDateTime cancelledAt;
        private String cancellationReason;
        private Double refundAmount;
        private String refundStatus;
    }
}
