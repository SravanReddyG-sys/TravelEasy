package com.traveleasy.bus.controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import com.traveleasy.bus.model.SeatSegmentReservation;
import com.traveleasy.bus.dto.SeatSegmentReservationDto;
import com.traveleasy.bus.service.SeatSegmentReservationService;



import com.traveleasy.bus.dto.TemplateSummaryDto;

import com.traveleasy.bus.dto.TripSeatLayoutResponse;

import com.traveleasy.bus.model.Bus;

import com.traveleasy.bus.model.BusSchedule;

import com.traveleasy.bus.model.Seat;

import com.traveleasy.bus.model.SeatLayoutTemplate;

import com.traveleasy.bus.repository.BusRepository;

import com.traveleasy.bus.repository.BusScheduleRepository;

import com.traveleasy.bus.repository.SeatRepository;

import com.traveleasy.bus.service.SeatLayoutService;
import com.traveleasy.bus.service.ScheduleService;
import com.traveleasy.bus.security.BusSecurityService;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.traveleasy.bus.dto.ScheduleStopDto;
import com.traveleasy.bus.dto.ScheduleConflictCheckRequest;
import com.traveleasy.bus.dto.ScheduleConflictCheckResponse;
import com.traveleasy.bus.model.ScheduleStop;
import com.traveleasy.bus.service.RouteStopsService;
import com.traveleasy.bus.dto.FareMatrixEntryDto;
import com.traveleasy.bus.dto.FareOverrideItemDto;
import com.traveleasy.bus.dto.ScheduleFareMatrixResponse;
import com.traveleasy.bus.dto.FareMatrixPreviewRequest;
import com.traveleasy.bus.service.FareMatrixService;
import com.traveleasy.bus.service.BusImageStorageService;
import com.traveleasy.bus.service.BusValidationService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;



import java.time.LocalDate;

import java.util.*;



@RestController

@RequestMapping("/api/buses")

@RequiredArgsConstructor

public class BusController {



    private final BusRepository busRepository;

    private final BusScheduleRepository scheduleRepository;

    private final SeatRepository seatRepository;

    private final SeatLayoutService seatLayoutService;
    private final BusSecurityService securityService;
    private final RouteStopsService routeStopsService;
    private final ScheduleService scheduleService;
    private final FareMatrixService fareMatrixService;
    private final SeatSegmentReservationService seatSegmentReservationService;
    private final com.traveleasy.bus.service.RouteService routeService;
    private final com.traveleasy.bus.repository.RouteRepository routeRepository;
    private final com.traveleasy.bus.repository.RouteStopRepository routeStopRepository;
    private final com.traveleasy.bus.repository.RoutePointRepository routePointRepository;
    private final BusImageStorageService imageStorageService;
    private final BusValidationService validationService;



    @org.springframework.beans.factory.annotation.Value("${seat.hold.duration.minutes:15}")

    private int seatHoldDurationMinutes;



    // ==================== TEMPLATE CATALOGUE ENDPOINTS ====================



    @GetMapping("/templates")

    public ResponseEntity<List<TemplateSummaryDto>> getAllTemplates() {

        return ResponseEntity.ok(seatLayoutService.getAllTemplateSummaries());

    }



    @GetMapping("/templates/{templateId}")

    public ResponseEntity<?> getTemplateDetails(@PathVariable String templateId) {

        return seatLayoutService.getTemplateById(templateId)

                .map(ResponseEntity::ok)

                .orElse(ResponseEntity.notFound().build());

    }



    @GetMapping("/templates/by-configuration/{configuration}")

    public ResponseEntity<List<TemplateSummaryDto>> getTemplatesByConfiguration(@PathVariable String configuration) {

        return ResponseEntity.ok(seatLayoutService.getTemplatesByConfiguration(configuration.toUpperCase()));

    }



    // ==================== BUS MANAGEMENT ====================

    @GetMapping("/check-registration")
    public ResponseEntity<?> checkRegistration(
            @RequestParam("busNumber") String busNumber,
            @RequestParam(value = "excludeBusId", required = false) Long excludeBusId) {
        if (busNumber == null || busNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Registration number parameter is required"));
        }
        String normalized = validationService.normalizeRegistrationNumber(busNumber);
        boolean validFormat = validationService.isValidRegistrationNumber(normalized);
        if (!validFormat) {
            return ResponseEntity.ok(Map.of(
                    "busNumber", busNumber,
                    "normalized", normalized,
                    "validFormat", false,
                    "available", false,
                    "message", "Invalid registration number format. Expected standard (e.g. KA01AB1234) or BH-series (e.g. 22BH1234AA)."
            ));
        }

        boolean taken = validationService.isRegistrationNumberTaken(normalized, excludeBusId);
        return ResponseEntity.ok(Map.of(
                "busNumber", busNumber,
                "normalized", normalized,
                "validFormat", true,
                "available", !taken,
                "message", taken ? "Registration number is already registered." : "Registration number is valid and available."
        ));
    }

    @PostMapping("/validate-basic-details")
    public ResponseEntity<?> validateBasicDetails(
            @RequestBody Bus bus,
            @RequestParam(value = "checkImages", defaultValue = "true") boolean checkImages,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        Map<String, String> errors = validationService.validateBasicDetails(bus, checkImages);
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "errors", errors,
                    "message", "Validation failed for Basic Details"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "Basic Details are valid"
        ));
    }

    @PostMapping("/images/upload")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to upload bus images"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        String callerRole = securityService.extractRole(token);
        if (!"ROLE_ADMIN".equalsIgnoreCase(callerRole) && !"ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }

        try {
            String filename = imageStorageService.storeImage(file, category);
            String imageUrl = "/api/buses/images/" + filename;
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "filename", filename,
                    "imageUrl", imageUrl,
                    "category", category != null ? category : "bus",
                    "size", file.getSize()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to upload image: " + e.getMessage()));
        }
    }

    @GetMapping("/images/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        try {
            Resource resource = imageStorageService.loadAsResource(filename);
            String contentType = "image/jpeg";
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png")) {
                contentType = "image/png";
            } else if (lower.endsWith(".webp")) {
                contentType = "image/webp";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(resource);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/images/{filename:.+}")
    public ResponseEntity<?> deleteImage(
            @PathVariable String filename,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to delete bus images"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        boolean deleted = imageStorageService.deleteImage(filename);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Image deleted successfully", "filename", filename));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Image not found or could not be deleted"));
        }
    }

    @PostMapping
    public ResponseEntity<?> addBus(
            @RequestBody Bus bus,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to register a bus"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        String callerRole = securityService.extractRole(token);
        if (callerRole == null || callerRole.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid role claim"));
        }

        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);

        if (!isAdmin && !isOperator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }

        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
        }

        // Authoritative assignment: Operators can only register buses for themselves
        if (isOperator) {
            bus.setOperatorId(callerUserId);
        } else if (isAdmin && (bus.getOperatorId() == null || bus.getOperatorId() <= 0)) {
            bus.setOperatorId(callerUserId);
        }

        if (bus.getStatus() == null || bus.getStatus().trim().isEmpty()) {
            bus.setStatus("ACTIVE");
        }

        // Normalize registration number, chassis, engine, and image mappings
        if (bus.getBusNumber() != null) {
            bus.setBusNumber(validationService.normalizeRegistrationNumber(bus.getBusNumber()));
        }
        if (bus.getChassisNumber() != null) {
            bus.setChassisNumber(bus.getChassisNumber().trim().toUpperCase());
        }
        if (bus.getEngineNumber() != null) {
            bus.setEngineNumber(bus.getEngineNumber().trim().toUpperCase());
        }
        if (bus.getExteriorImageUrl() != null && (bus.getImageUrl() == null || bus.getImageUrl().isBlank())) {
            bus.setImageUrl(bus.getExteriorImageUrl());
        }

        // Uniqueness validation
        if (bus.getBusNumber() != null && validationService.isRegistrationNumberTaken(bus.getBusNumber(), bus.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Registration Number '" + bus.getBusNumber() + "' is already registered."));
        }

        try {
            if (bus.getTemplateId() != null && !bus.getTemplateId().trim().isEmpty()) {
                seatLayoutService.validateAndAssignTemplate(bus, bus.getTemplateId(), bus.getBusConfiguration(), bus.getFacilityType());
            } else {
                // Fallback for legacy requests without templateId
                if (bus.getTotalSeats() == null || bus.getTotalSeats() <= 0) {
                    bus.setTotalSeats(36);
                }
                String fallbackId = bus.getTotalSeats() >= 48 ? SeatLayoutService.TEMPLATE_2_PLUS_2_SEATER
                        : (bus.getTotalSeats() <= 24 ? SeatLayoutService.TEMPLATE_1_PLUS_1_SEATER : SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER);
                seatLayoutService.validateAndAssignTemplate(bus, fallbackId, SeatLayoutService.CONFIG_SINGLE_DECK, bus.getFacilityType());
            }

            Bus saved = busRepository.save(bus);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Bus>> getAllBuses() {
        return ResponseEntity.ok(busRepository.findAll());
    }

    @GetMapping("/operator/{operatorId}")
    public ResponseEntity<?> getBusesByOperator(
            @PathVariable Long operatorId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to access operator buses"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        String callerRole = securityService.extractRole(token);
        if (callerRole == null || callerRole.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid role claim"));
        }

        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);

        if (!isAdmin && !isOperator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator role required"));
        }

        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
        }

        if (isOperator && !callerUserId.equals(operatorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Cannot access buses for another operator"));
        }

        return ResponseEntity.ok(busRepository.findByOperatorId(operatorId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBusById(@PathVariable Long id) {
        return busRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateBus(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to deactivate a bus"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        String callerRole = securityService.extractRole(token);
        if (callerRole == null || callerRole.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid role claim"));
        }

        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);

        if (!isAdmin && !isOperator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }

        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
        }

        Bus bus = busRepository.findById(id).orElse(null);
        if (bus == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Bus not found with id: " + id));
        }

        // TEST-03: Bus Ownership Enforcement
        if (isOperator && (bus.getOperatorId() == null || !bus.getOperatorId().equals(callerUserId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You do not have permission to modify this bus."));
        }

        // TEST-04: Deactivation Guard on Future Trips
        LocalDate today = LocalDate.now();
        java.time.LocalTime now = java.time.LocalTime.now();
        List<BusSchedule> candidates = scheduleRepository.findActiveUpcomingSchedulesByBusId(id, today);
        List<BusSchedule> activeUpcoming = candidates.stream().filter(s -> {
            if (s.getTravelDate().isAfter(today)) {
                return true;
            }
            if (s.getTravelDate().isEqual(today)) {
                if (s.getDepartureTime() != null) {
                    try {
                        java.time.LocalTime depTime = java.time.LocalTime.parse(s.getDepartureTime());
                        return depTime.isAfter(now);
                    } catch (Exception e) {
                        return true;
                    }
                }
                return true;
            }
            return false;
        }).toList();

        if (!activeUpcoming.isEmpty()) {
            int count = activeUpcoming.size();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Cannot deactivate bus: " + count + (count == 1 ? " active upcoming departure exists." : " active upcoming departures exist."),
                    "activeDeparturesCount", count
            ));
        }

        bus.setStatus("INACTIVE");
        Bus saved = busRepository.save(bus);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<?> activateBus(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to activate a bus"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        String callerRole = securityService.extractRole(token);
        if (callerRole == null || callerRole.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid role claim"));
        }

        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);

        if (!isAdmin && !isOperator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }

        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
        }

        Bus bus = busRepository.findById(id).orElse(null);
        if (bus == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Bus not found with id: " + id));
        }

        if (isOperator && (bus.getOperatorId() == null || !bus.getOperatorId().equals(callerUserId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You do not have permission to modify this bus."));
        }

        bus.setStatus("ACTIVE");
        Bus saved = busRepository.save(bus);
        return ResponseEntity.ok(saved);
    }

    // ==================== TRIP SCHEDULE & SEAT INVENTORY ====================

    @PostMapping("/schedules/check-conflict")
    public ResponseEntity<?> checkScheduleConflict(
            @RequestBody ScheduleConflictCheckRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to check schedule conflict"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        String callerRole = securityService.extractRole(token);
        if (callerRole == null || callerRole.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid role claim"));
        }

        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);

        if (!isAdmin && !isOperator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }

        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
        }

        ScheduleConflictCheckResponse response = scheduleService.checkConflict(callerUserId, isAdmin, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/schedules")
    public ResponseEntity<?> createSchedule(
            @RequestBody BusSchedule schedule,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to create trip schedule"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        String callerRole = securityService.extractRole(token);
        if (callerRole == null || callerRole.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid role claim"));
        }

        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);

        if (!isAdmin && !isOperator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }

        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
        }

        BusSchedule savedSchedule = scheduleService.createSchedule(callerUserId, isAdmin, schedule);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedSchedule);
    }

    @GetMapping("/operator/{operatorId}/schedules")
    public ResponseEntity<?> getOperatorSchedules(
            @PathVariable Long operatorId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to access operator schedules"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        String callerRole = securityService.extractRole(token);
        if (callerRole == null || callerRole.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid role claim"));
        }

        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);

        if (!isAdmin && !isOperator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator role required"));
        }

        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
        }

        if (isOperator && !callerUserId.equals(operatorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Cannot access schedules for another operator"));
        }

        List<BusSchedule> schedules = scheduleRepository.findByOperatorIdOrBusOperatorId(operatorId);
        return ResponseEntity.ok(schedules);

    }



    @GetMapping("/search")
    public ResponseEntity<List<BusSchedule>> searchBuses(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<BusSchedule> schedules = scheduleRepository.searchSchedulesWithRouteStops(origin, destination, date);
        for (BusSchedule s : schedules) {
            if (!s.getOrigin().equalsIgnoreCase(origin) || !s.getDestination().equalsIgnoreCase(destination)) {
                try {
                    List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(s.getId());
                    Integer fromOrder = null, toOrder = null;
                    for (ScheduleStop st : stops) {
                        if (st.getStopName().equalsIgnoreCase(origin.trim())) fromOrder = st.getStopOrder();
                        if (st.getStopName().equalsIgnoreCase(destination.trim())) toOrder = st.getStopOrder();
                    }
                    if (fromOrder != null && toOrder != null && fromOrder < toOrder) {
                        Double segFare = fareMatrixService.getEffectiveFare(s.getId(), fromOrder, toOrder);
                        if (segFare != null && segFare > 0) {
                            s.setFare(segFare);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return ResponseEntity.ok(schedules);
    }



    /**

     * Backward-compatible schedule details endpoint.

     */

    @GetMapping("/schedules/{scheduleId}")

    public ResponseEntity<?> getScheduleDetails(@PathVariable Long scheduleId) {

        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);

        if (schedule == null) {

            return ResponseEntity.notFound().build();

        }

        Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);

        List<Seat> seats = seatRepository.findByScheduleId(scheduleId);



        Map<String, Object> response = new HashMap<>();

        response.put("schedule", schedule);

        response.put("bus", bus);

        response.put("seats", seats);
        response.put("stops", routeStopsService.getStopsForSchedule(scheduleId));

        if (bus != null && bus.getTemplateId() != null) {

            seatLayoutService.getTemplateById(bus.getTemplateId()).ifPresent(tpl -> response.put("template", tpl));

        }



        return ResponseEntity.ok(response);

    }



    /**

     * Dedicated structured trip seat layout endpoint for future traveller seat-selection UI (Task 2).

     */

    @GetMapping("/schedules/{scheduleId}/layout")
    public ResponseEntity<?> getScheduleSeatLayout(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) Integer fromOrder,
            @RequestParam(required = false) Integer toOrder,
            @RequestParam(required = false) String fromStop,
            @RequestParam(required = false) String toStop) {

        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.notFound().build();
        }

        if (fromStop != null && toStop != null) {
            List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(scheduleId);
            for (ScheduleStop s : stops) {
                if (s.getStopName().equalsIgnoreCase(fromStop.trim())) fromOrder = s.getStopOrder();
                if (s.getStopName().equalsIgnoreCase(toStop.trim())) toOrder = s.getStopOrder();
            }
        }

        Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
        TripSeatLayoutResponse layout = seatLayoutService.getTripLayout(schedule, bus, fromOrder, toOrder);
        return ResponseEntity.ok(layout);
    }

    // ==================== SEAT BOOKING & RELEASE (SEGMENT-AWARE) ====================

    @Transactional
    @PutMapping("/schedules/{scheduleId}/book-seats")
    public ResponseEntity<?> bookSeats(
            @PathVariable Long scheduleId,
            @RequestBody List<String> seatNumbers,
            @RequestParam(required = false) String bookingRef,
            @RequestParam(required = false) Integer holdMinutes,
            @RequestParam(required = false) Integer fromOrder,
            @RequestParam(required = false) Integer toOrder,
            @RequestParam(required = false) String fromStop,
            @RequestParam(required = false) String toStop) {

        BusSchedule schedule = scheduleRepository.findByIdWithLock(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Schedule not found"));
        }

        if ("CANCELLED".equalsIgnoreCase(schedule.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Cannot book seats on a cancelled schedule."));
        }

        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No seats provided for booking"));
        }

        if (fromStop != null && toStop != null) {
            List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(scheduleId);
            for (ScheduleStop s : stops) {
                if (s.getStopName().equalsIgnoreCase(fromStop.trim())) fromOrder = s.getStopOrder();
                if (s.getStopName().equalsIgnoreCase(toStop.trim())) toOrder = s.getStopOrder();
            }
        }

        int duration = (holdMinutes != null && holdMinutes > 0) ? holdMinutes : seatHoldDurationMinutes;
        List<SeatSegmentReservation> reservedSegments = seatSegmentReservationService.reserveSeatsForSegments(
                scheduleId, seatNumbers, fromOrder, toOrder, bookingRef, duration);

        LocalDateTime holdExpiry = reservedSegments.isEmpty()
                ? LocalDateTime.now().plusMinutes(duration)
                : reservedSegments.get(0).getHoldExpiresAt();

        String finalBookingRef = reservedSegments.isEmpty()
                ? bookingRef
                : reservedSegments.get(0).getBookingRef();

        return ResponseEntity.ok(Map.of(
                "message", "Seats locked and held successfully for " + duration + " minutes",
                "schedule", schedule,
                "bookedSeats", seatNumbers,
                "bookingRef", finalBookingRef,
                "holdExpiresAt", holdExpiry != null ? holdExpiry.toString() : LocalDateTime.now().plusMinutes(duration).toString()
        ));
    }

    @Transactional
    @PutMapping("/schedules/{scheduleId}/confirm-seats")
    public ResponseEntity<?> confirmSeats(
            @PathVariable Long scheduleId,
            @RequestBody List<String> seatNumbers,
            @RequestParam(required = false) String bookingRef) {

        BusSchedule schedule = scheduleRepository.findByIdWithLock(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Schedule not found"));
        }

        if ("CANCELLED".equalsIgnoreCase(schedule.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Cannot confirm seats on a cancelled schedule."));
        }

        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No seats provided for confirmation"));
        }

        seatSegmentReservationService.confirmSeatsForSegments(scheduleId, seatNumbers, bookingRef);

        return ResponseEntity.ok(Map.of(
                "message", "Seats permanently confirmed",
                "confirmedSeats", seatNumbers
        ));
    }

    @Transactional
    @PutMapping("/schedules/{scheduleId}/release-seats")
    public ResponseEntity<?> releaseSeats(
            @PathVariable Long scheduleId,
            @RequestBody(required = false) List<String> seatNumbers,
            @RequestParam(required = false) String bookingRef) {

        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Schedule not found"));
        }

        int releasedCount = seatSegmentReservationService.releaseSeatsForSegments(scheduleId, seatNumbers, bookingRef);

        return ResponseEntity.ok(Map.of(
                "message", "Seats released successfully",
                "releasedCount", releasedCount
        ));
    }

    @Transactional
    @PutMapping("/schedules/{scheduleId}/status")
    public ResponseEntity<?> updateScheduleStatus(
            @PathVariable Long scheduleId,
            @RequestParam String status,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        BusSchedule schedule = scheduleRepository.findByIdWithLock(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Schedule not found with id: " + scheduleId));
        }

        boolean isInternal = "booking-service".equalsIgnoreCase(internalService);
        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to update schedule status"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }
            String callerRole = securityService.extractRole(token);
            boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
            boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);
            if (!isAdmin && !isOperator) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
            }
            Long callerUserId = securityService.extractUserId(token);
            if (callerUserId == null || callerUserId <= 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
            }
            if (isOperator) {
                Long ownerId = schedule.getOperatorId();
                if (ownerId == null && schedule.getBusId() != null) {
                    Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
                    if (bus != null) ownerId = bus.getOperatorId();
                }
                if (ownerId == null || !ownerId.equals(callerUserId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "You do not have permission to modify this schedule."));
                }
            }
        }

        schedule.setStatus(status.toUpperCase().trim());
        BusSchedule saved = scheduleRepository.save(schedule);
        return ResponseEntity.ok(Map.of(
                "message", "Schedule status updated successfully",
                "scheduleId", scheduleId,
                "status", saved.getStatus()
        ));
    }

    @Transactional
    @PutMapping("/schedules/{scheduleId}/release-all-seats")
    public ResponseEntity<?> releaseAllSeats(
            @PathVariable Long scheduleId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        BusSchedule schedule = scheduleRepository.findByIdWithLock(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Schedule not found with id: " + scheduleId));
        }

        boolean isInternal = "booking-service".equalsIgnoreCase(internalService);
        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to release seats"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }
            String callerRole = securityService.extractRole(token);
            boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
            boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);
            if (!isAdmin && !isOperator) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
            }
            Long callerUserId = securityService.extractUserId(token);
            if (callerUserId == null || callerUserId <= 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
            }
            if (isOperator) {
                Long ownerId = schedule.getOperatorId();
                if (ownerId == null && schedule.getBusId() != null) {
                    Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
                    if (bus != null) ownerId = bus.getOperatorId();
                }
                if (ownerId == null || !ownerId.equals(callerUserId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "You do not have permission to modify this schedule."));
                }
            }
        }

        List<Seat> seats = seatRepository.findByScheduleIdWithLock(scheduleId);
        int releasedCount = 0;
        for (Seat seat : seats) {
            boolean wasUnavailable = !"AVAILABLE".equalsIgnoreCase(seat.getStatus())
                    || seat.getHoldBookingRef() != null
                    || seat.getHoldExpiresAt() != null;
            if (wasUnavailable) {
                releasedCount++;
            }
            seat.setStatus("AVAILABLE");
            seat.setHoldExpiresAt(null);
            seat.setHoldBookingRef(null);
        }
        seatRepository.saveAll(seats);
        seatSegmentReservationService.releaseAllSegmentsForSchedule(scheduleId);

        schedule.setAvailableSeats(seats.size());
        scheduleRepository.save(schedule);

        return ResponseEntity.ok(Map.of(
                "message", "All seats released successfully",
                "scheduleId", scheduleId,
                "releasedCount", releasedCount,
                "totalSeats", seats.size()
        ));
    }

    // ==================== ROUTE STOPS & CONSECUTIVE SEGMENT FARES ====================

    @GetMapping("/schedules/{scheduleId}/stops")
    public ResponseEntity<List<ScheduleStop>> getScheduleStops(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(routeStopsService.getStopsForSchedule(scheduleId));
    }

    @PutMapping("/schedules/{scheduleId}/stops")
    public ResponseEntity<?> configureScheduleStops(
            @PathVariable Long scheduleId,
            @RequestBody List<ScheduleStopDto> stops,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Schedule not found with id: " + scheduleId));
        }

        boolean isInternal = "booking-service".equalsIgnoreCase(internalService);
        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to configure route stops"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }
            String callerRole = securityService.extractRole(token);
            boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
            boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);
            if (!isAdmin && !isOperator) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
            }
            Long callerUserId = securityService.extractUserId(token);
            if (callerUserId == null || callerUserId <= 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
            }
            if (isOperator) {
                Long ownerId = schedule.getOperatorId();
                if (ownerId == null && schedule.getBusId() != null) {
                    Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
                    if (bus != null) ownerId = bus.getOperatorId();
                }
                if (ownerId == null || !ownerId.equals(callerUserId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "You do not have permission to modify this schedule."));
                }
            }
        }

        List<ScheduleStop> configuredStops = routeStopsService.configureRouteStops(scheduleId, stops);
        return ResponseEntity.ok(Map.of(
                "message", "Route stops configured successfully",
                "scheduleId", scheduleId,
                "stops", configuredStops
        ));
    }

    // ==================== ORIGIN-DESTINATION (OD) FARE MATRIX & OVERRIDES ====================

    @PostMapping("/schedules/fare-matrix/preview")
    public ResponseEntity<ScheduleFareMatrixResponse> previewFareMatrix(@RequestBody FareMatrixPreviewRequest request) {
        if (request == null || request.getStops() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(fareMatrixService.previewFareMatrix(request.getStops(), request.getOverrides()));
    }

    @GetMapping("/schedules/{scheduleId}/fare-matrix")
    public ResponseEntity<ScheduleFareMatrixResponse> getScheduleFareMatrix(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(fareMatrixService.getFareMatrix(scheduleId));
    }

    @GetMapping("/schedules/{scheduleId}/fare")
    public ResponseEntity<?> getSegmentFare(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) Integer fromOrder,
            @RequestParam(required = false) Integer toOrder,
            @RequestParam(required = false) String fromStop,
            @RequestParam(required = false) String toStop) {

        Double effectiveFare;
        if (fromOrder != null && toOrder != null) {
            effectiveFare = fareMatrixService.getEffectiveFare(scheduleId, fromOrder, toOrder);
        } else if (fromStop != null && toStop != null) {
            effectiveFare = fareMatrixService.getEffectiveFareByStopNames(scheduleId, fromStop, toStop);
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Must provide either (fromOrder, toOrder) or (fromStop, toStop) query parameters"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "scheduleId", scheduleId,
                "effectiveFare", effectiveFare
        ));
    }

    @PutMapping("/schedules/{scheduleId}/fare-matrix/overrides")
    public ResponseEntity<?> updateFareOverrides(
            @PathVariable Long scheduleId,
            @RequestBody List<FareOverrideItemDto> overrides,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Schedule not found with id: " + scheduleId));
        }

        boolean isInternal = "booking-service".equalsIgnoreCase(internalService);
        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to update fare overrides"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }
            String callerRole = securityService.extractRole(token);
            boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
            boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);
            if (!isAdmin && !isOperator) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
            }
            Long callerUserId = securityService.extractUserId(token);
            if (callerUserId == null || callerUserId <= 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
            }
            if (isOperator) {
                Long ownerId = schedule.getOperatorId();
                if (ownerId == null && schedule.getBusId() != null) {
                    Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
                    if (bus != null) ownerId = bus.getOperatorId();
                }
                if (ownerId == null || !ownerId.equals(callerUserId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "You do not have permission to modify this schedule."));
                }
            }
        }

        ScheduleFareMatrixResponse response = fareMatrixService.saveFareOverrides(scheduleId, overrides);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/schedules/{scheduleId}/fare-matrix/overrides")
    public ResponseEntity<?> clearFareOverrides(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) Integer fromOrder,
            @RequestParam(required = false) Integer toOrder,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Schedule not found with id: " + scheduleId));
        }

        boolean isInternal = "booking-service".equalsIgnoreCase(internalService);
        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to clear fare overrides"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }
            String callerRole = securityService.extractRole(token);
            boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
            boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);
            if (!isAdmin && !isOperator) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
            }
            Long callerUserId = securityService.extractUserId(token);
            if (callerUserId == null || callerUserId <= 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
            }
            if (isOperator) {
                Long ownerId = schedule.getOperatorId();
                if (ownerId == null && schedule.getBusId() != null) {
                    Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
                    if (bus != null) ownerId = bus.getOperatorId();
                }
                if (ownerId == null || !ownerId.equals(callerUserId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "You do not have permission to modify this schedule."));
                }
            }
        }

        ScheduleFareMatrixResponse response;
        if (fromOrder != null && toOrder != null) {
            response = fareMatrixService.saveFareOverrides(scheduleId, List.of(
                    FareOverrideItemDto.builder()
                            .fromStopOrder(fromOrder)
                            .toStopOrder(toOrder)
                            .customFare(null)
                            .build()
            ));
        } else {
            response = fareMatrixService.clearAllOverrides(scheduleId);
        }
        return ResponseEntity.ok(response);
    }

    // ==================== SEGMENT RESERVATIONS INSPECTION ====================

    @GetMapping("/schedules/{scheduleId}/segment-reservations")
    public ResponseEntity<List<SeatSegmentReservationDto>> getSegmentReservations(@PathVariable Long scheduleId) {
        List<SeatSegmentReservation> reservations = seatSegmentReservationService.getScheduleReservations(scheduleId);
        List<SeatSegmentReservationDto> dtos = reservations.stream()
                .map(r -> SeatSegmentReservationDto.builder()
                        .id(r.getId())
                        .scheduleId(r.getScheduleId())
                        .seatNumber(r.getSeatNumber())
                        .segmentIndex(r.getSegmentIndex())
                        .fromStopOrder(r.getFromStopOrder())
                        .toStopOrder(r.getToStopOrder())
                        .bookingRef(r.getBookingRef())
                        .status(r.getStatus())
                        .holdExpiresAt(r.getHoldExpiresAt())
                        .createdAt(r.getCreatedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // ==================== ROUTE MANAGEMENT ENDPOINTS ====================

    @PostMapping("/routes")
    public ResponseEntity<?> createRoute(
            @RequestBody com.traveleasy.bus.dto.CreateRouteRequest req,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to create a route"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        String callerRole = securityService.extractRole(token);
        if (!"ROLE_ADMIN".equalsIgnoreCase(callerRole) && !"ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }
        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
        }

        com.traveleasy.bus.dto.RouteDto created = routeService.createRoute(callerUserId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/routes")
    public ResponseEntity<?> getOperatorRoutes(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to access routes"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        String callerRole = securityService.extractRole(token);
        if (!"ROLE_ADMIN".equalsIgnoreCase(callerRole) && !"ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }
        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
        }

        List<com.traveleasy.bus.dto.RouteDto> routes = routeService.getOperatorRoutes(callerUserId);
        return ResponseEntity.ok(routes);
    }

    @GetMapping("/routes/{id}")
    public ResponseEntity<?> getRouteById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to access route details"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        String callerRole = securityService.extractRole(token);
        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);
        if (!isAdmin && !isOperator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }
        Long callerUserId = securityService.extractUserId(token);

        com.traveleasy.bus.dto.RouteDto route = routeService.getRouteDetails(id, callerUserId, isAdmin);
        return ResponseEntity.ok(route);
    }

    @PutMapping("/routes/{id}")
    public ResponseEntity<?> updateRoute(
            @PathVariable Long id,
            @RequestBody com.traveleasy.bus.dto.CreateRouteRequest req,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to update route"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        String callerRole = securityService.extractRole(token);
        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);
        if (!isAdmin && !isOperator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }
        Long callerUserId = securityService.extractUserId(token);

        com.traveleasy.bus.dto.RouteDto updated = routeService.updateRoute(id, callerUserId, isAdmin, req);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/routes/{id}/status")
    public ResponseEntity<?> updateRouteStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to modify route status"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        String callerRole = securityService.extractRole(token);
        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);
        if (!isAdmin && !isOperator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }
        Long callerUserId = securityService.extractUserId(token);
        String newStatus = body != null ? body.get("status") : null;
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Status is required (ACTIVE or INACTIVE)"));
        }

        com.traveleasy.bus.dto.RouteDto updated = routeService.changeRouteStatus(id, callerUserId, isAdmin, newStatus);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/routes/{id}/schedules")
    public ResponseEntity<?> getRouteSchedules(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to view route schedules"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        String callerRole = securityService.extractRole(token);
        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);
        if (!isAdmin && !isOperator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Bus Operator or Admin role required"));
        }
        Long callerUserId = securityService.extractUserId(token);

        List<BusSchedule> schedules = routeService.getRouteSchedules(id, callerUserId, isAdmin);
        return ResponseEntity.ok(schedules);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "status", ex.getStatusCode().value(),
                        "error", ex.getStatusCode().toString(),
                        "message", ex.getReason() != null ? ex.getReason() : ex.getMessage()
                ));
    }
}
