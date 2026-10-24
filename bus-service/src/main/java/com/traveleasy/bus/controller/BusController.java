package com.traveleasy.bus.controller;

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
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public ResponseEntity<?> addBus(@RequestBody Bus bus) {
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
    public ResponseEntity<List<Bus>> getBusesByOperator(@PathVariable Long operatorId) {
        return ResponseEntity.ok(busRepository.findByOperatorId(operatorId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBusById(@PathVariable Long id) {
        return busRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== TRIP SCHEDULE & SEAT INVENTORY ====================

    @PostMapping("/schedules")
    public ResponseEntity<?> createSchedule(@RequestBody BusSchedule schedule) {
        Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
        if (bus == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bus not found with id: " + schedule.getBusId()));
        }

        schedule.setOperatorName(bus.getOperatorName());
        schedule.setBusNumber(bus.getBusNumber());
        schedule.setBusType(bus.getBusType());

        BusSchedule savedSchedule = scheduleRepository.save(schedule);

        // Generate physical seats from the authoritative layout template
        seatLayoutService.generateTripSeats(savedSchedule, bus);
        scheduleRepository.save(savedSchedule);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedSchedule);
    }

    @GetMapping("/search")
    public ResponseEntity<List<BusSchedule>> searchBuses(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(scheduleRepository.findByOriginIgnoreCaseAndDestinationIgnoreCaseAndTravelDate(origin, destination, date));
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
        if (bus != null && bus.getTemplateId() != null) {
            seatLayoutService.getTemplateById(bus.getTemplateId()).ifPresent(tpl -> response.put("template", tpl));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Dedicated structured trip seat layout endpoint for future traveller seat-selection UI (Task 2).
     */
    @GetMapping("/schedules/{scheduleId}/layout")
    public ResponseEntity<?> getScheduleSeatLayout(@PathVariable Long scheduleId) {
        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.notFound().build();
        }
        Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
        TripSeatLayoutResponse layout = seatLayoutService.getTripLayout(schedule, bus);
        return ResponseEntity.ok(layout);
    }

    // ==================== SEAT BOOKING & RELEASE ====================

    @PutMapping("/schedules/{scheduleId}/book-seats")
    public ResponseEntity<?> bookSeats(
            @PathVariable Long scheduleId,
            @RequestBody List<String> seatNumbers,
            @RequestParam(required = false) String bookingRef,
            @RequestParam(required = false) Integer holdMinutes) {
        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Schedule not found"));
        }

        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No seats provided for booking"));
        }

        int duration = (holdMinutes != null && holdMinutes > 0) ? holdMinutes : seatHoldDurationMinutes;
        java.time.LocalDateTime holdExpiry = java.time.LocalDateTime.now().plusMinutes(duration);

        List<Seat> bookedSeatsList = new ArrayList<>();
        for (String seatNum : seatNumbers) {
            Seat seat = seatRepository.findByScheduleIdAndSeatNumber(scheduleId, seatNum).orElse(null);
            if (seat == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Seat " + seatNum + " does not exist for this schedule"));
            }
            if (!Boolean.TRUE.equals(seat.getIsBookable())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Seat " + seatNum + " is a non-bookable physical space"));
            }

            // Check if held by an expired reservation (safe to reclaim)
            boolean isExpiredHold = seat.getHoldExpiresAt() != null && seat.getHoldExpiresAt().isBefore(java.time.LocalDateTime.now());

            if (!"AVAILABLE".equalsIgnoreCase(seat.getStatus()) && !isExpiredHold) {
                return ResponseEntity.badRequest().body(Map.of("message", "Seat " + seatNum + " is not available (Status: " + seat.getStatus() + ")"));
            }

            seat.setStatus("LOCKED");
            seat.setHoldExpiresAt(holdExpiry);
            if (bookingRef != null && !bookingRef.isBlank()) {
                seat.setHoldBookingRef(bookingRef);
            }
            bookedSeatsList.add(seat);
        }

        seatRepository.saveAll(bookedSeatsList);
        schedule.setAvailableSeats(Math.max(0, schedule.getAvailableSeats() - seatNumbers.size()));
        scheduleRepository.save(schedule);

        return ResponseEntity.ok(Map.of(
                "message", "Seats locked and held successfully for " + duration + " minutes",
                "schedule", schedule,
                "bookedSeats", seatNumbers,
                "holdExpiresAt", holdExpiry.toString()
        ));
    }

    @PutMapping("/schedules/{scheduleId}/confirm-seats")
    public ResponseEntity<?> confirmSeats(@PathVariable Long scheduleId, @RequestBody List<String> seatNumbers) {
        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Schedule not found"));
        }

        for (String seatNum : seatNumbers) {
            Optional<Seat> optSeat = seatRepository.findByScheduleIdAndSeatNumber(scheduleId, seatNum);
            if (optSeat.isPresent()) {
                Seat seat = optSeat.get();
                seat.setStatus("BOOKED");
                seat.setHoldExpiresAt(null);
                seatRepository.save(seat);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Seats permanently confirmed"));
    }

    @PutMapping("/schedules/{scheduleId}/release-seats")
    public ResponseEntity<?> releaseSeats(@PathVariable Long scheduleId, @RequestBody List<String> seatNumbers) {
        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Schedule not found"));
        }

        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No seats provided to release"));
        }

        int releasedCount = 0;
        for (String seatNum : seatNumbers) {
            Optional<Seat> optSeat = seatRepository.findByScheduleIdAndSeatNumber(scheduleId, seatNum);
            if (optSeat.isPresent()) {
                Seat seat = optSeat.get();
                if ("BOOKED".equalsIgnoreCase(seat.getStatus()) || "LOCKED".equalsIgnoreCase(seat.getStatus())) {
                    seat.setStatus("AVAILABLE");
                    seat.setHoldExpiresAt(null);
                    seat.setHoldBookingRef(null);
                    seatRepository.save(seat);
                    releasedCount++;
                }
            }
        }

        schedule.setAvailableSeats(schedule.getAvailableSeats() + releasedCount);
        scheduleRepository.save(schedule);

        return ResponseEntity.ok(Map.of(
                "message", "Seats released successfully",
                "releasedCount", releasedCount
        ));
    }
}
