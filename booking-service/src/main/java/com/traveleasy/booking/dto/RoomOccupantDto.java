package com.traveleasy.booking.dto;

import com.traveleasy.booking.model.GuestGender;
import com.traveleasy.booking.model.GuestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual occupant of a physical room (WP-HB-06.1, extended in WP-HB-UI-03).
 * Supports typed adult or child designation with adult age (>=18) & gender, and child age (0-17).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomOccupantDto {

    private String name;
    private GuestType type;       // ADULT or CHILD
    private Integer age;          // CHILD: 0-17; ADULT: >= 18
    private GuestGender gender;   // ADULT: required; CHILD: null
}
