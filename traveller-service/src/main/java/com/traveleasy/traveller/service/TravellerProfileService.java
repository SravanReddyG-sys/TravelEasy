package com.traveleasy.traveller.service;

import com.traveleasy.traveller.dto.TravellerProfileDto;
import com.traveleasy.traveller.dto.UpdateProfileRequest;
import com.traveleasy.traveller.model.TravellerProfile;
import com.traveleasy.traveller.repository.TravellerProfileRepository;
import com.traveleasy.traveller.security.TravellerPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TravellerProfileService {

    private final TravellerProfileRepository profileRepository;

    @Transactional
    public TravellerProfileDto getProfile(TravellerPrincipal principal) {
        TravellerProfile profile = profileRepository.findByUserId(principal.getUserId())
                .orElseGet(() -> profileRepository.save(TravellerProfile.builder()
                        .userId(principal.getUserId())
                        .build()));

        return mapToDto(profile, principal);
    }

    @Transactional
    public TravellerProfileDto updateProfile(TravellerPrincipal principal, UpdateProfileRequest request) {
        TravellerProfile profile = profileRepository.findByUserId(principal.getUserId())
                .orElseGet(() -> TravellerProfile.builder().userId(principal.getUserId()).build());

        if (request.getDateOfBirth() != null) profile.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getAddress() != null) profile.setAddress(request.getAddress().trim());
        if (request.getCity() != null) profile.setCity(request.getCity().trim());
        if (request.getState() != null) profile.setState(request.getState().trim());
        if (request.getPincode() != null) profile.setPincode(request.getPincode().trim());
        if (request.getEmergencyContactName() != null) profile.setEmergencyContactName(request.getEmergencyContactName().trim());
        if (request.getEmergencyContactPhone() != null) profile.setEmergencyContactPhone(request.getEmergencyContactPhone().trim());
        if (request.getIdProofType() != null) profile.setIdProofType(request.getIdProofType().trim());
        if (request.getIdProofNumber() != null) profile.setIdProofNumber(request.getIdProofNumber().trim());
        profile.setUpdatedAt(LocalDateTime.now());

        TravellerProfile saved = profileRepository.save(profile);
        return mapToDto(saved, principal);
    }

    public int calculateCompletionPercentage(TravellerProfile profile) {
        if (profile == null) return 20; // base auth info provides name/email
        int score = 20; // from basic auth account
        if (profile.getDateOfBirth() != null) score += 15;
        if (profile.getGender() != null && !profile.getGender().isEmpty()) score += 15;
        if (profile.getAddress() != null && !profile.getAddress().isEmpty()) score += 15;
        if (profile.getCity() != null && !profile.getCity().isEmpty()) score += 10;
        if (profile.getEmergencyContactName() != null && !profile.getEmergencyContactName().isEmpty()) score += 15;
        if (profile.getIdProofNumber() != null && !profile.getIdProofNumber().isEmpty()) score += 10;
        return Math.min(100, score);
    }

    private TravellerProfileDto mapToDto(TravellerProfile profile, TravellerPrincipal principal) {
        int percentage = calculateCompletionPercentage(profile);
        return TravellerProfileDto.builder()
                .id(profile.getId())
                .userId(principal.getUserId())
                .fullName(principal.getFullName())
                .email(principal.getEmail())
                .dateOfBirth(profile.getDateOfBirth())
                .gender(profile.getGender())
                .address(profile.getAddress())
                .city(profile.getCity())
                .state(profile.getState())
                .pincode(profile.getPincode())
                .emergencyContactName(profile.getEmergencyContactName())
                .emergencyContactPhone(profile.getEmergencyContactPhone())
                .idProofType(profile.getIdProofType())
                .idProofNumber(profile.getIdProofNumber())
                .profileComplete(percentage >= 80)
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
