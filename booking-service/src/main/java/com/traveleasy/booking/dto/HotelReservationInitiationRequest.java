package com.traveleasy.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Traveller request payload to initiate a consolidated multi-room hotel reservation (WP-HB-05).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelReservationInitiationRequest {

    private String bookingRef; // Optional client-requested or idempotency key

    private Long customerId;
    private String customerName;
    private String customerEmail;

    private Long hotelId;
    private String hotelName;

    private LocalDate checkInDate;
    private LocalDate checkOutDate;

    private List<ReservationAllocationItemDto> allocations;

    /**
     * WP-HB-06 compatibility: optional guest allocation details payload
     */
    private String guestDetailsJson;
}
