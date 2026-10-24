package com.traveleasy.traveller.service;

import com.traveleasy.traveller.dto.TravellerPreferenceDto;
import com.traveleasy.traveller.dto.UpdatePreferenceRequest;
import com.traveleasy.traveller.model.TravellerPreference;
import com.traveleasy.traveller.repository.TravellerPreferenceRepository;
import com.traveleasy.traveller.security.TravellerPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TravellerPreferenceService {

    private final TravellerPreferenceRepository preferenceRepository;

    @Transactional
    public TravellerPreferenceDto getPreferences(TravellerPrincipal principal) {
        TravellerPreference pref = preferenceRepository.findByUserId(principal.getUserId())
                .orElseGet(() -> preferenceRepository.save(TravellerPreference.builder()
                        .userId(principal.getUserId())
                        .build()));

        return mapToDto(pref);
    }

    @Transactional
    public TravellerPreferenceDto updatePreferences(TravellerPrincipal principal, UpdatePreferenceRequest request) {
        TravellerPreference pref = preferenceRepository.findByUserId(principal.getUserId())
                .orElseGet(() -> TravellerPreference.builder().userId(principal.getUserId()).build());

        if (request.getSeatPreference() != null) pref.setSeatPreference(request.getSeatPreference().trim().toUpperCase());
        if (request.getBerthPreference() != null) pref.setBerthPreference(request.getBerthPreference().trim().toUpperCase());
        if (request.getMealPreference() != null) pref.setMealPreference(request.getMealPreference().trim().toUpperCase());
        if (request.getPreferredCurrency() != null) pref.setPreferredCurrency(request.getPreferredCurrency().trim().toUpperCase());
        if (request.getEmailNotifications() != null) pref.setEmailNotifications(request.getEmailNotifications());
        if (request.getSmsNotifications() != null) pref.setSmsNotifications(request.getSmsNotifications());
        pref.setUpdatedAt(LocalDateTime.now());

        TravellerPreference saved = preferenceRepository.save(pref);
        return mapToDto(saved);
    }

    private TravellerPreferenceDto mapToDto(TravellerPreference pref) {
        return TravellerPreferenceDto.builder()
                .id(pref.getId())
                .userId(pref.getUserId())
                .seatPreference(pref.getSeatPreference())
                .berthPreference(pref.getBerthPreference())
                .mealPreference(pref.getMealPreference())
                .preferredCurrency(pref.getPreferredCurrency())
                .emailNotifications(pref.isEmailNotifications())
                .smsNotifications(pref.isSmsNotifications())
                .updatedAt(pref.getUpdatedAt())
                .build();
    }
}
