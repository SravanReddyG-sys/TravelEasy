package com.traveleasy.traveller.controller;

import com.traveleasy.traveller.dto.CreateSupportTicketRequest;
import com.traveleasy.traveller.dto.SupportTicketDto;
import com.traveleasy.traveller.security.TravellerPrincipal;
import com.traveleasy.traveller.service.TravellerSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/traveller/support")
@RequiredArgsConstructor
public class TravellerSupportController {

    private final TravellerSupportService supportService;

    @GetMapping("/tickets")
    public ResponseEntity<List<SupportTicketDto>> getTickets(@AuthenticationPrincipal TravellerPrincipal principal) {
        return ResponseEntity.ok(supportService.getTickets(principal));
    }

    @PostMapping("/tickets")
    public ResponseEntity<SupportTicketDto> createTicket(@AuthenticationPrincipal TravellerPrincipal principal,
                                                        @RequestBody CreateSupportTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supportService.createTicket(principal, request));
    }
}
