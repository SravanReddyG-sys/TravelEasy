package com.traveleasy.traveller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferenceRequest {
    private String seatPreference;
    private String berthPreference;
    private String mealPreference;
    private String preferredCurrency;
    private Boolean emailNotifications;
    private Boolean smsNotifications;
}
