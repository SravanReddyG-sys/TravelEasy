package com.traveleasy.hotel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Consolidated multi-allocation hotel reservation request payload (WP-HB-04).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsolidatedReservationRequestDto {

    @NotBlank(message = "Booking reference is mandatory")
    private String bookingRef;

    @NotNull(message = "Hotel ID is mandatory")
    private Long hotelId;

    @NotNull(message = "Check-in date is mandatory")
    private LocalDate checkInDate;

    @NotNull(message = "Check-out date is mandatory")
    private LocalDate checkOutDate;

    /**
     * Authoritative reservation expiry deadline propagated from Booking Service (WP-HB-05).
     */
    private LocalDateTime expiresAt;

    @NotEmpty(message = "At least one room allocation is required")
    @Valid
    private List<ReservationAllocationRequestDto> allocations;
}
