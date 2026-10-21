package com.traveleasy.traveller.controller;

import com.traveleasy.traveller.dto.TravellerPreferenceDto;
import com.traveleasy.traveller.dto.UpdatePreferenceRequest;
import com.traveleasy.traveller.security.TravellerPrincipal;
import com.traveleasy.traveller.service.TravellerPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/traveller/preferences")
@RequiredArgsConstructor
public class TravellerPreferenceController {

    private final TravellerPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<TravellerPreferenceDto> getPreferences(@AuthenticationPrincipal TravellerPrincipal principal) {
        return ResponseEntity.ok(preferenceService.getPreferences(principal));
    }

    @PutMapping
    public ResponseEntity<TravellerPreferenceDto> updatePreferences(@AuthenticationPrincipal TravellerPrincipal principal,
                                                                   @RequestBody UpdatePreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.updatePreferences(principal, request));
    }
}
