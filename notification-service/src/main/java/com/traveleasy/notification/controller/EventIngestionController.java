package com.traveleasy.notification.controller;

import com.traveleasy.notification.dto.EventIngestionResponse;
import com.traveleasy.notification.dto.NotificationEventRequest;
import com.traveleasy.notification.security.NotificationSecurityService;
import com.traveleasy.notification.service.EventIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class EventIngestionController {

    private final EventIngestionService eventIngestionService;
    private final NotificationSecurityService notificationSecurityService;

    @PostMapping("/events")
    public ResponseEntity<?> ingestEvent(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody NotificationEventRequest request) {

        if (request == null || request.getEventType() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "BAD_REQUEST",
                    "message", "NotificationEventRequest and eventType are required."
            ));
        }

        // 1. DTO and payload validation
        try {
            eventIngestionService.validateRequest(request);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "BAD_REQUEST",
                    "message", iae.getMessage()
            ));
        }

        // 2. Authenticate service and enforce event ownership
        String authenticatedService;
        try {
            authenticatedService = notificationSecurityService.authenticateInternalService(authHeader, request.getEventType());
        } catch (SecurityException se) {
            String msg = se.getMessage();
            HttpStatus status = (msg != null && msg.contains("Forbidden")) ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
            return ResponseEntity.status(status).body(Map.of(
                    "status", status.name(),
                    "message", msg != null ? msg : "Unauthorized internal service request."
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "UNAUTHORIZED",
                    "message", "Authentication failed: " + ex.getMessage()
            ));
        }

        // 3. Ingest event with concurrent collision protection
        try {
            EventIngestionResponse response = eventIngestionService.ingestEvent(request, authenticatedService);
            if ("ALREADY_INGESTED".equals(response.getStatus())) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (DataIntegrityViolationException dive) {
            // Concurrent race condition: another thread inserted the same event_id simultaneously
            log.info("Concurrent collision handled for eventId {}. Returning ALREADY_INGESTED.", request.getEventId());
            EventIngestionResponse duplicateResponse = EventIngestionResponse.builder()
                    .status("ALREADY_INGESTED")
                    .eventId(request.getEventId() != null ? request.getEventId().trim() : "")
                    .message("Event has already been ingested and recorded.")
                    .ingestedAt(Instant.now())
                    .build();
            return ResponseEntity.ok(duplicateResponse);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "BAD_REQUEST",
                    "message", iae.getMessage()
            ));
        } catch (Exception ex) {
            log.error("Internal error ingesting event {}: {}", request.getEventId(), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "INTERNAL_SERVER_ERROR",
                    "message", "Failed to ingest notification event: " + ex.getMessage()
            ));
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", "BAD_REQUEST",
                "message", "Malformed request payload or invalid enum values: " + ex.getMessage()
        ));
    }
}
