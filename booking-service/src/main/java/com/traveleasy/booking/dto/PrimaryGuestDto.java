package com.traveleasy.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Primary guest contact information representing the overall reservation contact (WP-HB-06.1).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrimaryGuestDto {

    private String fullName;
    private String email;
    private String mobile;
}
