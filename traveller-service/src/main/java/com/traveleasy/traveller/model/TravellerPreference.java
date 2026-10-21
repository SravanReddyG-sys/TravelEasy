package com.traveleasy.traveller.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "traveller_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravellerPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Builder.Default
    private String seatPreference = "WINDOW"; // WINDOW, AISLE, NO_PREFERENCE

    @Builder.Default
    private String berthPreference = "LOWER"; // LOWER, UPPER, NO_PREFERENCE

    @Builder.Default
    private String mealPreference = "VEG"; // VEG, NON_VEG, JAIN, ANY

    @Builder.Default
    private String preferredCurrency = "INR";

    @Builder.Default
    private boolean emailNotifications = true;

    @Builder.Default
    private boolean smsNotifications = true;

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
