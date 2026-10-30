package com.traveleasy.bus.service;

import com.traveleasy.bus.dto.FareMatrixEntryDto;
import com.traveleasy.bus.dto.FareOverrideItemDto;
import com.traveleasy.bus.dto.ScheduleFareMatrixResponse;
import com.traveleasy.bus.dto.ScheduleStopDto;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.ScheduleFareOverride;
import com.traveleasy.bus.model.ScheduleStop;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.ScheduleFareOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FareMatrixService {

    private final RouteStopsService routeStopsService;
    private final ScheduleFareOverrideRepository fareOverrideRepository;
    private final BusScheduleRepository scheduleRepository;

    /**
     * Computes the complete Origin-Destination (OD) fare matrix for a schedule.
     * Generates all N*(N-1)/2 pairs with both calculated elementary sums and effective fares.
     */
    public ScheduleFareMatrixResponse getFareMatrix(Long scheduleId) {
        List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(scheduleId);
        if (stops == null || stops.size() < 2) {
            return ScheduleFareMatrixResponse.builder()
                    .scheduleId(scheduleId)
                    .totalStops(0)
                    .totalPairs(0)
                    .stops(Collections.emptyList())
                    .matrix(Collections.emptyList())
                    .build();
        }

        int n = stops.size();
        int totalPairs = (n * (n - 1)) / 2;

        // Load existing manual overrides into quick lookup map
        List<ScheduleFareOverride> overrides = fareOverrideRepository.findByScheduleId(scheduleId);
        Map<String, Double> overrideMap = new HashMap<>();
        for (ScheduleFareOverride ov : overrides) {
            overrideMap.put(ov.getFromStopOrder() + "-" + ov.getToStopOrder(), ov.getCustomFare());
        }

        List<FareMatrixEntryDto> matrix = new ArrayList<>(totalPairs);
        for (int i = 0; i < n - 1; i++) {
            ScheduleStop fromStop = stops.get(i);
            double runningSum = 0.0;

            for (int j = i + 1; j < n; j++) {
                ScheduleStop toStop = stops.get(j);
                // Add elementary segment fare for stops.get(j - 1)
                Double segFare = stops.get(j - 1).getConsecutiveFare();
                runningSum += (segFare != null ? segFare : 0.0);

                Double distance = null;
                if (fromStop.getDistanceKm() != null && toStop.getDistanceKm() != null) {
                    distance = Math.max(0.0, toStop.getDistanceKm() - fromStop.getDistanceKm());
                }

                String pairKey = i + "-" + j;
                Double customFare = overrideMap.get(pairKey);
                double effectiveFare = (customFare != null) ? customFare : runningSum;
                boolean isOverridden = (customFare != null);

                matrix.add(FareMatrixEntryDto.builder()
                        .fromStopOrder(i)
                        .fromStopName(fromStop.getStopName())
                        .toStopOrder(j)
                        .toStopName(toStop.getStopName())
                        .distanceKm(distance)
                        .calculatedFare(runningSum)
                        .customFare(customFare)
                        .effectiveFare(effectiveFare)
                        .isOverridden(isOverridden)
                        .segmentCount(j - i)
                        .build());
            }
        }

        List<ScheduleStopDto> stopDtos = stops.stream()
                .map(s -> ScheduleStopDto.builder()
                        .id(s.getId())
                        .stopOrder(s.getStopOrder())
                        .stopName(s.getStopName())
                        .stopType(s.getStopType())
                        .arrivalTime(s.getArrivalTime())
                        .departureTime(s.getDepartureTime())
                        .distanceKm(s.getDistanceKm())
                        .consecutiveFare(s.getConsecutiveFare())
                        .build())
                .toList();

        return ScheduleFareMatrixResponse.builder()
                .scheduleId(scheduleId)
                .totalStops(n)
                .totalPairs(totalPairs)
                .stops(stopDtos)
                .matrix(matrix)
                .build();
    }

    /**
     * Previews the complete Origin-Destination (OD) fare matrix in memory for a proposed
     * route stops configuration and optional manual fare overrides.
     * Generates all N*(N-1)/2 pairs with calculated elementary sums and effective fares.
     */
    public ScheduleFareMatrixResponse previewFareMatrix(List<ScheduleStopDto> stops, List<FareOverrideItemDto> overrides) {
        if (stops == null || stops.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least 2 stops are required to preview a fare matrix");
        }

        int n = stops.size();
        int totalPairs = (n * (n - 1)) / 2;

        Map<String, Double> overrideMap = new HashMap<>();
        if (overrides != null) {
            for (FareOverrideItemDto ov : overrides) {
                if (ov.getFromStopOrder() != null && ov.getToStopOrder() != null && ov.getCustomFare() != null && ov.getCustomFare() > 0) {
                    overrideMap.put(ov.getFromStopOrder() + "-" + ov.getToStopOrder(), ov.getCustomFare());
                }
            }
        }

        List<FareMatrixEntryDto> matrix = new ArrayList<>(totalPairs);
        for (int i = 0; i < n - 1; i++) {
            ScheduleStopDto fromStop = stops.get(i);
            double runningSum = 0.0;

            for (int j = i + 1; j < n; j++) {
                ScheduleStopDto toStop = stops.get(j);
                Double segFare = stops.get(j - 1).getConsecutiveFare();
                runningSum += (segFare != null ? segFare : 0.0);

                Double distance = null;
                if (fromStop.getDistanceKm() != null && toStop.getDistanceKm() != null) {
                    distance = Math.max(0.0, toStop.getDistanceKm() - fromStop.getDistanceKm());
                }

                String pairKey = i + "-" + j;
                Double customFare = overrideMap.get(pairKey);
                double effectiveFare = (customFare != null) ? customFare : runningSum;
                boolean isOverridden = (customFare != null);

                matrix.add(FareMatrixEntryDto.builder()
                        .fromStopOrder(i)
                        .fromStopName(fromStop.getStopName())
                        .toStopOrder(j)
                        .toStopName(toStop.getStopName())
                        .distanceKm(distance)
                        .calculatedFare(runningSum)
                        .customFare(customFare)
                        .effectiveFare(effectiveFare)
                        .isOverridden(isOverridden)
                        .segmentCount(j - i)
                        .build());
            }
        }

        return ScheduleFareMatrixResponse.builder()
                .scheduleId(null)
                .totalStops(n)
                .totalPairs(totalPairs)
                .stops(stops)
                .matrix(matrix)
                .build();
    }

    /**
     * Resolves the effective price for a journey segment between two stops.
     */
    public Double getEffectiveFare(Long scheduleId, Integer fromStopOrder, Integer toStopOrder) {
        if (fromStopOrder == null || toStopOrder == null || fromStopOrder >= toStopOrder) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stop orders: fromStopOrder must be strictly less than toStopOrder");
        }

        // 1. Check if a manual override is registered
        Optional<ScheduleFareOverride> override = fareOverrideRepository
                .findByScheduleIdAndFromStopOrderAndToStopOrder(scheduleId, fromStopOrder, toStopOrder);
        if (override.isPresent()) {
            return override.get().getCustomFare();
        }

        // 2. Fall back to automatic cumulative segment summation
        List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(scheduleId);
        if (stops == null || stops.isEmpty() || toStopOrder >= stops.size() || fromStopOrder < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stop orders out of bounds for schedule " + scheduleId);
        }

        double calculatedSum = 0.0;
        for (int k = fromStopOrder; k < toStopOrder; k++) {
            Double segFare = stops.get(k).getConsecutiveFare();
            calculatedSum += (segFare != null ? segFare : 0.0);
        }
        return calculatedSum;
    }

    /**
     * Resolves the effective price by stop names (case-insensitive).
     */
    public Double getEffectiveFareByStopNames(Long scheduleId, String fromStopName, String toStopName) {
        if (fromStopName == null || toStopName == null || fromStopName.trim().equalsIgnoreCase(toStopName.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Boarding and dropping stop names must be distinct");
        }

        List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(scheduleId);
        Integer fromOrder = null;
        Integer toOrder = null;

        for (ScheduleStop s : stops) {
            if (s.getStopName().equalsIgnoreCase(fromStopName.trim())) {
                fromOrder = s.getStopOrder();
            }
            if (s.getStopName().equalsIgnoreCase(toStopName.trim())) {
                toOrder = s.getStopOrder();
            }
        }

        if (fromOrder == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Boarding stop '" + fromStopName + "' not found on route for schedule " + scheduleId);
        }
        if (toOrder == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dropping stop '" + toStopName + "' not found on route for schedule " + scheduleId);
        }
        if (fromOrder >= toOrder) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Boarding stop '" + fromStopName + "' occurs after or at dropping stop '" + toStopName + "'");
        }

        return getEffectiveFare(scheduleId, fromOrder, toOrder);
    }

    /**
     * Saves or removes manual fare overrides for Origin-Destination pairs.
     * Wrapped in @Transactional to guarantee atomic consistency with parent BusSchedule fare.
     */
    @Transactional
    public ScheduleFareMatrixResponse saveFareOverrides(Long scheduleId, List<FareOverrideItemDto> overrideDtos) {
        BusSchedule schedule = scheduleRepository.findByIdWithLock(scheduleId).orElse(null);
        if (schedule == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + scheduleId);
        }

        if ("CANCELLED".equalsIgnoreCase(schedule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify fare overrides on a cancelled schedule");
        }

        List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(scheduleId);
        int n = stops.size();
        if (n < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule does not have a valid multi-stop route configured");
        }

        Map<Integer, String> stopNameMap = new HashMap<>();
        for (ScheduleStop s : stops) {
            stopNameMap.put(s.getStopOrder(), s.getStopName());
        }

        for (FareOverrideItemDto item : overrideDtos) {
            Integer fromOrder = item.getFromStopOrder();
            Integer toOrder = item.getToStopOrder();

            if (fromOrder == null || toOrder == null || fromOrder < 0 || toOrder >= n || fromOrder >= toOrder) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid stop order pair [" + fromOrder + " -> " + toOrder + "]. Must satisfy 0 <= from < to <= " + (n - 1));
            }

            Double customFare = item.getCustomFare();
            if (customFare == null || customFare <= 0.0) {
                // Clear manual override
                fareOverrideRepository.deleteByScheduleIdAndFromStopOrderAndToStopOrder(scheduleId, fromOrder, toOrder);
            } else {
                // Upsert manual override
                Optional<ScheduleFareOverride> optOverride = fareOverrideRepository
                        .findByScheduleIdAndFromStopOrderAndToStopOrder(scheduleId, fromOrder, toOrder);

                ScheduleFareOverride override;
                if (optOverride.isPresent()) {
                    override = optOverride.get();
                    override.setCustomFare(customFare);
                    override.setUpdatedAt(LocalDateTime.now());
                } else {
                    override = ScheduleFareOverride.builder()
                            .scheduleId(scheduleId)
                            .fromStopOrder(fromOrder)
                            .toStopOrder(toOrder)
                            .fromStopName(stopNameMap.get(fromOrder))
                            .toStopName(stopNameMap.get(toOrder))
                            .customFare(customFare)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                }
                fareOverrideRepository.save(override);
            }
        }
        fareOverrideRepository.flush();

        // Check if full trip (0 -> N-1) effective fare changed; update BusSchedule.fare accordingly
        Double fullTripFare = getEffectiveFare(scheduleId, 0, n - 1);
        schedule.setFare(fullTripFare);
        scheduleRepository.save(schedule);

        log.info("Saved {} fare overrides for schedule {}. Full-trip fare synchronized to: {}", overrideDtos.size(), scheduleId, fullTripFare);
        return getFareMatrix(scheduleId);
    }

    /**
     * Resets all manual fare overrides for a schedule, reverting the entire matrix to elementary cumulative sums.
     */
    @Transactional
    public ScheduleFareMatrixResponse clearAllOverrides(Long scheduleId) {
        BusSchedule schedule = scheduleRepository.findByIdWithLock(scheduleId).orElse(null);
        if (schedule == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + scheduleId);
        }

        if ("CANCELLED".equalsIgnoreCase(schedule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot clear fare overrides on a cancelled schedule");
        }

        fareOverrideRepository.deleteByScheduleId(scheduleId);
        fareOverrideRepository.flush();

        // Synchronize parent BusSchedule fare back to default elementary sum
        List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(scheduleId);
        if (stops.size() >= 2) {
            double calculatedFullTrip = 0.0;
            for (int k = 0; k < stops.size() - 1; k++) {
                Double segFare = stops.get(k).getConsecutiveFare();
                calculatedFullTrip += (segFare != null ? segFare : 0.0);
            }
            schedule.setFare(calculatedFullTrip);
            scheduleRepository.save(schedule);
        }

        log.info("Cleared all manual fare overrides for schedule {}.", scheduleId);
        return getFareMatrix(scheduleId);
    }
}
