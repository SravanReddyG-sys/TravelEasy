package com.traveleasy.traveller.controller;

import com.traveleasy.traveller.dto.TravellerDashboardResponse;
import com.traveleasy.traveller.security.TravellerPrincipal;
import com.traveleasy.traveller.service.TravellerDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/traveller")
@RequiredArgsConstructor
public class TravellerDashboardController {

    private final TravellerDashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<TravellerDashboardResponse> getDashboard(@AuthenticationPrincipal TravellerPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getDashboard(principal));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "traveller-service",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
