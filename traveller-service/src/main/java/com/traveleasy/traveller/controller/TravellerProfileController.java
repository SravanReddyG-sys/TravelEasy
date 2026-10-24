package com.traveleasy.traveller.controller;

import com.traveleasy.traveller.dto.TravellerProfileDto;
import com.traveleasy.traveller.dto.UpdateProfileRequest;
import com.traveleasy.traveller.security.TravellerPrincipal;
import com.traveleasy.traveller.service.TravellerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/traveller/profile")
@RequiredArgsConstructor
public class TravellerProfileController {

    private final TravellerProfileService profileService;

    @GetMapping
    public ResponseEntity<TravellerProfileDto> getProfile(@AuthenticationPrincipal TravellerPrincipal principal) {
        return ResponseEntity.ok(profileService.getProfile(principal));
    }

    @PutMapping
    public ResponseEntity<TravellerProfileDto> updateProfile(@AuthenticationPrincipal TravellerPrincipal principal,
                                                            @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.updateProfile(principal, request));
    }
}
