package com.traveleasy.booking.dto;

import com.traveleasy.booking.model.GuestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual occupant of a physical room (WP-HB-06.1).
 * Supports typed adult or child designation with age validation for children.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomOccupantDto {

    private String name;
    private GuestType type; // ADULT or CHILD
    private Integer age;    // Child's age (0-17); null for adults
}
