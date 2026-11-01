package com.traveleasy.bus.service;

import com.traveleasy.bus.dto.ScheduleStopDto;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.ScheduleStop;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.ScheduleStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteStopsService {

    private final ScheduleStopRepository scheduleStopRepository;
    private final BusScheduleRepository scheduleRepository;
    private final com.traveleasy.bus.repository.ScheduleFareOverrideRepository fareOverrideRepository;

    /**
     * Validates that the stop sequence is sound:
     * - At least 2 stops (origin & destination).
     * - Distinct stop names along the route.
     * - Strict order index sequence (0, 1, ... N-1).
     * - Positive consecutive segment fare for all elementary segments (k to k+1).
     */
    public void validateStops(List<ScheduleStopDto> stopDtos) {
        if (stopDtos == null || stopDtos.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A route must contain at least 2 stops (origin and destination).");
        }

        Set<String> seenNames = new HashSet<>();
        for (int i = 0; i < stopDtos.size(); i++) {
            ScheduleStopDto dto = stopDtos.get(i);
            if (dto.getStopName() == null || dto.getStopName().trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stop name at position " + i + " cannot be blank.");
            }

            String normName = dto.getStopName().trim().toLowerCase();
            if (seenNames.contains(normName)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate stop name detected on route: " + dto.getStopName().trim());
            }
            seenNames.add(normName);

            if (dto.getStopOrder() != null && !dto.getStopOrder().equals(i)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stop order mismatch at position " + i + ": expected " + i + " but found " + dto.getStopOrder());
            }

            // Elementary consecutive segment fare check (for all stops except the final destination)
            if (i < stopDtos.size() - 1) {
                Double fare = dto.getConsecutiveFare();
                if (fare == null || fare <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consecutive segment fare for stop '" + dto.getStopName().trim() + "' to next stop must be greater than 0.");
                }
            }
        }
    }

    /**
     * Atomically configures or updates the ordered route stops and consecutive segment fares for a schedule.
     * Also updates the parent BusSchedule's origin, destination, timings, and default full-trip fare.
     */
    @Transactional
    public List<ScheduleStop> configureRouteStops(Long scheduleId, List<ScheduleStopDto> stopDtos) {
        BusSchedule schedule = scheduleRepository.findByIdWithLock(scheduleId).orElse(null);
        if (schedule == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + scheduleId);
        }

        if ("CANCELLED".equalsIgnoreCase(schedule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify route stops on a cancelled schedule.");
        }

        validateStops(stopDtos);

        // Delete previous stops and outdated fare overrides if any
        scheduleStopRepository.deleteByScheduleId(scheduleId);
        scheduleStopRepository.flush();
        fareOverrideRepository.deleteByScheduleId(scheduleId);
        fareOverrideRepository.flush();

        List<ScheduleStop> entities = new ArrayList<>();
        double totalFare = 0.0;
        int lastIndex = stopDtos.size() - 1;

        for (int i = 0; i <= lastIndex; i++) {
            ScheduleStopDto dto = stopDtos.get(i);
            double fare = (dto.getConsecutiveFare() != null) ? dto.getConsecutiveFare() : 0.0;
            if (i < lastIndex) {
                totalFare += fare;
            }

            String stopType = (i == 0) ? "ORIGIN" : (i == lastIndex ? "DESTINATION" : "INTERMEDIATE");

            ScheduleStop stop = ScheduleStop.builder()
                    .scheduleId(scheduleId)
                    .stopOrder(i)
                    .stopName(dto.getStopName().trim())
                    .stopType(stopType)
                    .arrivalTime(dto.getArrivalTime())
                    .departureTime(dto.getDepartureTime())
                    .arrivalDate(dto.getArrivalDate())
                    .departureDate(dto.getDepartureDate())
                    .arrivalDateTime(dto.getArrivalDateTime())
                    .departureDateTime(dto.getDepartureDateTime())
                    .haltMinutes(dto.getHaltMinutes())
                    .distanceKm(dto.getDistanceKm())
                    .consecutiveFare(i == lastIndex ? 0.0 : fare)
                    .build();

            if (dto.getPoints() != null && !dto.getPoints().isEmpty()) {
                stop.setPoints(dto.getPoints());
            }
            entities.add(stop);
        }

        List<ScheduleStop> savedStops = scheduleStopRepository.saveAll(entities);

        // Synchronize parent BusSchedule with Route Origin, Destination, Departure/Arrival, and Cumulative Fare
        ScheduleStop originStop = savedStops.get(0);
        ScheduleStop destStop = savedStops.get(lastIndex);

        schedule.setOrigin(originStop.getStopName());
        schedule.setDestination(destStop.getStopName());
        if (originStop.getDepartureTime() != null && !originStop.getDepartureTime().isBlank()) {
            schedule.setDepartureTime(originStop.getDepartureTime());
        }
        if (destStop.getArrivalTime() != null && !destStop.getArrivalTime().isBlank()) {
            schedule.setArrivalTime(destStop.getArrivalTime());
        }
        schedule.setFare(totalFare);
        scheduleRepository.save(schedule);

        log.info("Configured {} route stops for schedule {} (Total calculated fare: {})", savedStops.size(), scheduleId, totalFare);
        return savedStops;
    }

    /**
     * Retrieves ordered route stops for a schedule.
     * If no explicit intermediate stops exist in DB, synthesizes the default 2-stop route (Origin -> Destination)
     * for full backward compatibility with legacy single-segment schedules.
     */
    public List<ScheduleStop> getStopsForSchedule(Long scheduleId) {
        List<ScheduleStop> stops = scheduleStopRepository.findByScheduleIdOrderByStopOrderAsc(scheduleId);
        if (!stops.isEmpty()) {
            return stops;
        }

        // Backward compatibility fallback for pre-existing schedules
        BusSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return Collections.emptyList();
        }

        List<ScheduleStop> syntheticStops = new ArrayList<>();
        syntheticStops.add(ScheduleStop.builder()
                .scheduleId(scheduleId)
                .stopOrder(0)
                .stopName(schedule.getOrigin() != null ? schedule.getOrigin() : "Origin")
                .stopType("ORIGIN")
                .departureTime(schedule.getDepartureTime())
                .consecutiveFare(schedule.getFare() != null ? schedule.getFare() : 0.0)
                .build());

        syntheticStops.add(ScheduleStop.builder()
                .scheduleId(scheduleId)
                .stopOrder(1)
                .stopName(schedule.getDestination() != null ? schedule.getDestination() : "Destination")
                .stopType("DESTINATION")
                .arrivalTime(schedule.getArrivalTime())
                .consecutiveFare(0.0)
                .build());

        return syntheticStops;
    }
}
