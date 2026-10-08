package com.traveleasy.bus.controller;

import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.Seat;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/buses")
@RequiredArgsConstructor
public class BusController {

    private final BusRepository busRepository;
    private final BusScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;

    @PostMapping
    public ResponseEntity<Bus> addBus(@RequestBody Bus bus) {
        if (bus.getTotalSeats() == null || bus.getTotalSeats() <= 0) {
            bus.setTotalSeats(30);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(busRepository.save(bus));
    }

    @GetMapping
    public ResponseEntity<List<Bus>> getAllBuses() {
        return ResponseEntity.ok(busRepository.findAll());
    }

    @GetMapping("/operator/{operatorId}")
    public ResponseEntity<List<Bus>> getBusesByOperator(@PathVariable Long operatorId) {
        return ResponseEntity.ok(busRepository.findByOperatorId(operatorId));
    }

    @PostMapping("/schedules")
    public ResponseEntity<BusSchedule> createSchedule(@RequestBody BusSchedule schedule) {
        Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
        if (bus != null) {
            schedule.setOperatorName(bus.getOperatorName());
            schedule.setBusNumber(bus.getBusNumber());
            schedule.setBusType(bus.getBusType());
            if (schedule.getAvailableSeats() == null) {
                schedule.setAvailableSeats(bus.getTotalSeats());
            }
        }
        BusSchedule savedSchedule = scheduleRepository.save(schedule);

        // Auto-generate seats for this schedule
        int totalSeats = bus != null && bus.getTotalSeats() != null ? bus.getTotalSeats() : 30;
        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= totalSeats; i++) {
            String seatNum = (i <= totalSeats / 2 ? "L" : "U") + (i <= totalSeats / 2 ? i : (i - totalSeats / 2));
            seats.add(Seat.builder()
                    .scheduleId(savedSchedule.getId())
                    .seatNumber(seatNum)
                    .seatType(i % 3 == 0 ? "Sleeper" : "Seater")
                    .price(savedSchedule.getFare() != null ? savedSchedule.getFare() : 500.0)
                    .status("AVAILABLE")
                    .build());
        }
        seatRepository.saveAll(seats);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedSchedule);
    }

    @GetMapping("/search")
    public ResponseEntity<List<BusSchedule>> searchBuses(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(scheduleRepository.findByOriginIgnoreCaseAndDestinationIgnoreCaseAndTravelDate(origin, destination, date));
    }

    @GetMapping("/schedules/{scheduleId}")
    public ResponseEntity<?> getScheduleDetails(@PathVariable Long scheduleId) {
        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.notFound().build();
        }
        List<Seat> seats = seatRepository.findByScheduleId(scheduleId);
        return ResponseEntity.ok(Map.of("schedule", schedule, "seats", seats));
    }

    @PutMapping("/schedules/{scheduleId}/book-seats")
    public ResponseEntity<?> bookSeats(@PathVariable Long scheduleId, @RequestBody List<String> seatNumbers) {
        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Schedule not found"));
        }

        List<Seat> bookedSeatsList = new ArrayList<>();
        for (String seatNum : seatNumbers) {
            Seat seat = seatRepository.findByScheduleIdAndSeatNumber(scheduleId, seatNum).orElse(null);
            if (seat == null || !"AVAILABLE".equalsIgnoreCase(seat.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Seat " + seatNum + " is not available"));
            }
            seat.setStatus("BOOKED");
            bookedSeatsList.add(seat);
        }

        seatRepository.saveAll(bookedSeatsList);
        schedule.setAvailableSeats(Math.max(0, schedule.getAvailableSeats() - seatNumbers.size()));
        scheduleRepository.save(schedule);

        return ResponseEntity.ok(Map.of("message", "Seats booked successfully", "schedule", schedule, "bookedSeats", seatNumbers));
    }

    @PutMapping("/schedules/{scheduleId}/release-seats")
    public ResponseEntity<?> releaseSeats(@PathVariable Long scheduleId, @RequestBody List<String> seatNumbers) {
        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) return ResponseEntity.badRequest().build();

        for (String seatNum : seatNumbers) {
            seatRepository.findByScheduleIdAndSeatNumber(scheduleId, seatNum).ifPresent(seat -> {
                seat.setStatus("AVAILABLE");
                seatRepository.save(seat);
            });
        }
        schedule.setAvailableSeats(schedule.getAvailableSeats() + seatNumbers.size());
        scheduleRepository.save(schedule);

        return ResponseEntity.ok(Map.of("message", "Seats released successfully"));
    }
}
