package com.traveleasy.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationAllocationItemDto {

    private Long roomId;
    private Integer quantity;

    /**
     * WP-HB-06.1: Physical room-level guest allocations.
     */
    @Builder.Default
    private List<RoomGuestAllocationDto> rooms = new ArrayList<>();
}
