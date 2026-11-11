package com.traveleasy.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Room-level guest allocation for one physical room within a room type (WP-HB-06.1).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomGuestAllocationDto {

    private Integer roomSequence;
    private Integer adults;
    private Integer children;

    @Builder.Default
    private List<RoomOccupantDto> occupants = new ArrayList<>();
}
