package com.traveleasy.traveller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravellerPreferenceDto {
    private Long id;
    private Long userId;
    private String seatPreference;
    private String berthPreference;
    private String mealPreference;
    private String preferredCurrency;
    private boolean emailNotifications;
    private boolean smsNotifications;
    private LocalDateTime updatedAt;
}
