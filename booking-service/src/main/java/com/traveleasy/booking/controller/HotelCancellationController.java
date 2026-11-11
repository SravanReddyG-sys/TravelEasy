package com.traveleasy.booking.controller;

import com.traveleasy.booking.dto.HotelCancellationQuoteResponse;
import com.traveleasy.booking.dto.HotelCancellationRequest;
import com.traveleasy.booking.dto.HotelCancellationResponse;
import com.traveleasy.booking.service.HotelCancellationOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Dedicated HOTEL cancellation controller (WP-HC-01).
 * Strictly isolated from legacy/BUS cancellation endpoints on BookingController.
 */
@RestController
@RequestMapping("/api/bookings/hotel")
@RequiredArgsConstructor
public class HotelCancellationController {

    private final HotelCancellationOrchestrationService orchestrationService;

    @GetMapping("/{id}/cancellation-quote")
    public ResponseEntity<?> getCancellationQuoteById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        try {
            HotelCancellationQuoteResponse quote = orchestrationService.getCancellationQuoteById(
                    id, authHeader, internalToken, internalService);
            return ResponseEntity.ok(quote);
        } catch (ResponseStatusException ex) {
            return errorResponse(ex);
        }
    }

    @GetMapping("/ref/{bookingRef}/cancellation-quote")
    public ResponseEntity<?> getCancellationQuoteByRef(
            @PathVariable String bookingRef,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        try {
            HotelCancellationQuoteResponse quote = orchestrationService.getCancellationQuoteByRef(
                    bookingRef, authHeader, internalToken, internalService);
            return ResponseEntity.ok(quote);
        } catch (ResponseStatusException ex) {
            return errorResponse(ex);
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelHotelBookingById(
            @PathVariable Long id,
            @RequestBody(required = false) HotelCancellationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        try {
            HotelCancellationResponse response = orchestrationService.cancelHotelBookingById(
                    id, request, authHeader, internalToken, internalService);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException ex) {
            return errorResponse(ex);
        }
    }

    @PutMapping("/ref/{bookingRef}/cancel")
    public ResponseEntity<?> cancelHotelBookingByRef(
            @PathVariable String bookingRef,
            @RequestBody(required = false) HotelCancellationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        try {
            HotelCancellationResponse response = orchestrationService.cancelHotelBookingByRef(
                    bookingRef, request, authHeader, internalToken, internalService);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException ex) {
            return errorResponse(ex);
        }
    }

    private ResponseEntity<Map<String, Object>> errorResponse(ResponseStatusException ex) {
        String message = ex.getReason() != null && !ex.getReason().isBlank() ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "message", message != null ? message : "Hotel cancellation request failed",
                        "status", ex.getStatusCode().value()
                ));
    }
}
