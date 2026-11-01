package com.traveleasy.bus.service;

import com.traveleasy.bus.dto.ScheduleStopDto;
import com.traveleasy.bus.dto.ScheduleConflictCheckRequest;
import com.traveleasy.bus.dto.ScheduleConflictCheckResponse;
import com.traveleasy.bus.model.*;
import com.traveleasy.bus.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final BusRepository busRepository;
    private final BusScheduleRepository scheduleRepository;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final RoutePointRepository routePointRepository;
    private final RouteStopsService routeStopsService;
    private final SeatLayoutService seatLayoutService;
    private final FareMatrixService fareMatrixService;

    @Value("${schedule.turnaround.buffer.minutes:60}")
    private long turnaroundBufferMinutes = 60;

    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public Clock getClock() {
        return this.clock;
    }

    public void setTurnaroundBufferMinutes(long turnaroundBufferMinutes) {
        this.turnaroundBufferMinutes = turnaroundBufferMinutes;
    }

    public long getTurnaroundBufferMinutes() {
        return this.turnaroundBufferMinutes;
    }

    /**
     * Creates and validates a new bus schedule with pessimistic write locking,
     * turnaround buffer interval conflict checking, cross-midnight support,
     * route snapshotting, and seat inventory generation.
     */
    @Transactional(rollbackFor = Exception.class)
    public BusSchedule createSchedule(Long callerUserId, boolean isAdmin, BusSchedule schedule) {
        if (schedule == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule payload is required");
        }

        if (schedule.getBusId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bus ID is required");
        }

        // 1. Acquire pessimistic write lock on the Bus record to serialize schedule creation per bus
        Bus bus = busRepository.findByIdWithLock(schedule.getBusId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bus not found with id: " + schedule.getBusId()));

        if (!isAdmin && (bus.getOperatorId() == null || !bus.getOperatorId().equals(callerUserId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this bus");
        }

        if ("INACTIVE".equalsIgnoreCase(bus.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot schedule trips on an inactive bus.");
        }

        // 2. Validate travel date, departure/arrival times, cross-midnight dates, and fares
        validateScheduleDateTimeAndFare(schedule);

        // 3. Prevent overlapping bus assignments (operating intervals with turnaround buffer)
        checkBusAvailability(bus, schedule);

        // 4. Validate and snapshot route configuration if routeId is provided
        validateAndSnapshotRoute(callerUserId, isAdmin, schedule);

        // 5. Populate bus and operator metadata
        schedule.setOperatorId(bus.getOperatorId());
        schedule.setOperatorName(bus.getOperatorName());
        schedule.setBusNumber(bus.getBusNumber());
        schedule.setBusType(bus.getBusType());
        if (schedule.getStatus() == null || schedule.getStatus().trim().isEmpty()) {
            schedule.setStatus("SCHEDULED");
        }

        // 6. Persist schedule and generate physical seat inventory
        BusSchedule savedSchedule = scheduleRepository.save(schedule);
        seatLayoutService.generateTripSeats(savedSchedule, bus);
        savedSchedule = scheduleRepository.save(savedSchedule);

        // 7. Configure route stops if present
        if (schedule.getStops() != null && !schedule.getStops().isEmpty()) {
            routeStopsService.configureRouteStops(savedSchedule.getId(), schedule.getStops());
            savedSchedule = scheduleRepository.findById(savedSchedule.getId()).orElse(savedSchedule);
        }

        // 8. Configure manual fare overrides if present
        if (schedule.getFareOverrides() != null && !schedule.getFareOverrides().isEmpty()) {
            fareMatrixService.saveFareOverrides(savedSchedule.getId(), schedule.getFareOverrides());
            savedSchedule = scheduleRepository.findById(savedSchedule.getId()).orElse(savedSchedule);
        }

        log.info("Successfully created schedule #{} for bus '{}' (ID: {}) from {} to {} on {}",
                savedSchedule.getId(), bus.getBusNumber(), bus.getId(),
                savedSchedule.getOrigin(), savedSchedule.getDestination(), savedSchedule.getTravelDate());

        return savedSchedule;
    }

    /**
     * Validates date-times, formats, past departure constraint, positive fares,
     * and derives explicit departureDateTime and arrivalDateTime (including cross-midnight).
     */
    public void validateScheduleDateTimeAndFare(BusSchedule schedule) {
        if (schedule.getTravelDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Travel date is required");
        }

        // Derive fare, origin, destination, and times from stops if caller provided stops payload
        if (schedule.getStops() != null && !schedule.getStops().isEmpty()) {
            int lastIdx = schedule.getStops().size() - 1;
            if (schedule.getOrigin() == null || schedule.getOrigin().trim().isEmpty()) {
                if (schedule.getStops().get(0).getStopName() != null) {
                    schedule.setOrigin(schedule.getStops().get(0).getStopName().trim());
                }
            }
            if (schedule.getDestination() == null || schedule.getDestination().trim().isEmpty()) {
                if (schedule.getStops().get(lastIdx).getStopName() != null) {
                    schedule.setDestination(schedule.getStops().get(lastIdx).getStopName().trim());
                }
            }
            if ((schedule.getDepartureTime() == null || schedule.getDepartureTime().trim().isEmpty())
                    && schedule.getStops().get(0).getDepartureTime() != null) {
                schedule.setDepartureTime(schedule.getStops().get(0).getDepartureTime().trim());
            }
            if ((schedule.getArrivalTime() == null || schedule.getArrivalTime().trim().isEmpty())
                    && schedule.getStops().get(lastIdx).getArrivalTime() != null) {
                schedule.setArrivalTime(schedule.getStops().get(lastIdx).getArrivalTime().trim());
            }
            if (schedule.getFare() == null || schedule.getFare() <= 0) {
                double computedFare = 0.0;
                for (int i = 0; i < lastIdx; i++) {
                    Double f = schedule.getStops().get(i).getConsecutiveFare();
                    if (f != null && f > 0) {
                        computedFare += f;
                    }
                }
                if (computedFare > 0) {
                    schedule.setFare(computedFare);
                }
            }
            for (ScheduleStopDto stop : schedule.getStops()) {
                if (stop.getConsecutiveFare() != null && stop.getConsecutiveFare() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consecutive stop fare cannot be negative");
                }
            }
        }

        if (schedule.getFare() == null || schedule.getFare() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fare must be greater than zero");
        }

        // Support backward compatibility for legacy tests that omit departure or arrival times
        if (schedule.getDepartureTime() == null || schedule.getDepartureTime().trim().isEmpty()) {
            schedule.setDepartureTime("06:00");
        }
        if (schedule.getArrivalTime() == null || schedule.getArrivalTime().trim().isEmpty()) {
            schedule.setArrivalTime("12:00");
        }

        LocalTime depTime = parseAndValidateTime(schedule.getDepartureTime(), "departureTime");
        LocalTime arrTime = parseAndValidateTime(schedule.getArrivalTime(), "arrivalTime");

        LocalDateTime depDateTime = LocalDateTime.of(schedule.getTravelDate(), depTime);
        LocalDateTime now = LocalDateTime.now(this.clock);

        if (depDateTime.isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Departure time cannot be in the past. Attempted departure: " + depDateTime + ", current time: " + now);
        }

        LocalDate arrDate;
        if (schedule.getArrivalDate() != null) {
            arrDate = schedule.getArrivalDate();
        } else {
            arrDate = schedule.getTravelDate();
            // If arrival time is earlier than or equal to departure time, journey crosses midnight
            if (arrTime.isBefore(depTime)) {
                arrDate = arrDate.plusDays(1);
            }
            schedule.setArrivalDate(arrDate);
        }

        LocalDateTime arrDateTime = LocalDateTime.of(arrDate, arrTime);

        if (!arrDateTime.isAfter(depDateTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Arrival date-time (" + arrDateTime + ") must be strictly after departure date-time (" + depDateTime + ").");
        }

        schedule.setDepartureDateTime(depDateTime);
        schedule.setArrivalDateTime(arrDateTime);

        // Validate chronological consistency across stops and individual points if provided
        if (schedule.getStops() != null && !schedule.getStops().isEmpty()) {
            validateStopAndPointTimings(schedule, schedule.getStops());
        }

        // Validate manual fare overrides if provided
        if (schedule.getFareOverrides() != null) {
            for (com.traveleasy.bus.dto.FareOverrideItemDto ov : schedule.getFareOverrides()) {
                if (ov.getFromStopOrder() == null || ov.getToStopOrder() == null || ov.getFromStopOrder() >= ov.getToStopOrder()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid fare override stop order pair");
                }
                if (ov.getCustomFare() != null && ov.getCustomFare() <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Overridden fare must be strictly greater than 0");
                }
            }
        }
    }

    /**
     * Comprehensive chronological validation for intermediate stops and individual points.
     */
    public void validateStopAndPointTimings(BusSchedule schedule, List<ScheduleStopDto> stops) {
        if (stops == null || stops.isEmpty()) {
            return;
        }

        LocalDateTime tripDep = schedule.getDepartureDateTime();
        LocalDateTime tripArr = schedule.getArrivalDateTime();

        LocalDateTime prevStopDep = tripDep;

        for (int i = 0; i < stops.size(); i++) {
            ScheduleStopDto stop = stops.get(i);
            boolean isOrigin = (i == 0);
            boolean isDestination = (i == stops.size() - 1);

            LocalDateTime stopArr = null;
            LocalDateTime stopDep = null;

            // Stop arrival validation
            if (!isOrigin && stop.getArrivalTime() != null && !stop.getArrivalTime().isBlank()) {
                LocalTime arrT = parseAndValidateTime(stop.getArrivalTime(), "Stop " + i + " arrivalTime");
                LocalDate arrD = stop.getArrivalDate();
                if (arrD == null) {
                    if (tripDep != null) {
                        arrD = arrT.isBefore(tripDep.toLocalTime()) ? tripDep.toLocalDate().plusDays(1) : tripDep.toLocalDate();
                    } else {
                        arrD = schedule.getTravelDate();
                    }
                    stop.setArrivalDate(arrD);
                }
                stopArr = LocalDateTime.of(arrD, arrT);
                stop.setArrivalDateTime(stopArr);

                if (prevStopDep != null && stopArr.isBefore(prevStopDep)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            String.format("Stop '%s' arrival (%s) cannot precede previous stop departure (%s).",
                                    stop.getStopName(), stopArr, prevStopDep));
                }
            } else if (isOrigin) {
                stop.setArrivalDate(null);
                stop.setArrivalTime(null);
                stop.setArrivalDateTime(null);
                if (stopDep == null && tripDep != null) {
                    stop.setDepartureDate(tripDep.toLocalDate());
                    stop.setDepartureTime(schedule.getDepartureTime());
                    stopDep = tripDep;
                    stop.setDepartureDateTime(stopDep);
                }
            }

            // Stop departure validation
            if (!isDestination && stop.getDepartureTime() != null && !stop.getDepartureTime().isBlank()) {
                LocalTime depT = parseAndValidateTime(stop.getDepartureTime(), "Stop " + i + " departureTime");
                LocalDate depD = stop.getDepartureDate();
                if (depD == null) {
                    depD = (stopArr != null) ? stopArr.toLocalDate() : (tripDep != null ? tripDep.toLocalDate() : schedule.getTravelDate());
                    stop.setDepartureDate(depD);
                }
                stopDep = LocalDateTime.of(depD, depT);
                stop.setDepartureDateTime(stopDep);

                if (stopArr != null) {
                    if (stopDep.isBefore(stopArr)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                String.format("Stop '%s' departure (%s) cannot precede its arrival (%s).",
                                        stop.getStopName(), stopDep, stopArr));
                    }
                    long halt = java.time.Duration.between(stopArr, stopDep).toMinutes();
                    stop.setHaltMinutes((int) halt);
                }
                prevStopDep = stopDep;
            } else if (isDestination) {
                stop.setDepartureDate(null);
                stop.setDepartureTime(null);
                stop.setDepartureDateTime(null);
            }

            // Destination arrival synchronization & check
            if (isDestination && tripArr != null) {
                if (stopArr == null) {
                    stop.setArrivalDate(tripArr.toLocalDate());
                    stop.setArrivalTime(schedule.getArrivalTime());
                    stopArr = tripArr;
                    stop.setArrivalDateTime(stopArr);
                } else {
                    if (stop.getArrivalDate() == null) {
                        stop.setArrivalDate(tripArr.toLocalDate());
                        stopArr = LocalDateTime.of(tripArr.toLocalDate(), stopArr.toLocalTime());
                        stop.setArrivalDateTime(stopArr);
                    }
                    if (!stopArr.isEqual(tripArr)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                String.format("Destination stop '%s' arrival (%s) does not match scheduled trip arrival (%s).",
                                        stop.getStopName(), stopArr, tripArr));
                    }
                }
            }

            // Validate individual points within stop
            if (stop.getPoints() != null && !stop.getPoints().isEmpty()) {
                for (com.traveleasy.bus.dto.SchedulePointDto pt : stop.getPoints()) {
                    LocalDateTime ptArr = null;
                    LocalDateTime ptDep = null;

                    if (pt.getArrivalTime() != null && !pt.getArrivalTime().isBlank()) {
                        LocalTime t = parseAndValidateTime(pt.getArrivalTime(), "Point " + pt.getPointName() + " arrivalTime");
                        LocalDate d = pt.getArrivalDate() != null ? pt.getArrivalDate() : (stopArr != null ? stopArr.toLocalDate() : (tripDep != null ? tripDep.toLocalDate() : schedule.getTravelDate()));
                        ptArr = LocalDateTime.of(d, t);
                        pt.setArrivalDate(d);
                        pt.setArrivalDateTime(ptArr);
                    }

                    if (pt.getDepartureTime() != null && !pt.getDepartureTime().isBlank()) {
                        LocalTime t = parseAndValidateTime(pt.getDepartureTime(), "Point " + pt.getPointName() + " departureTime");
                        LocalDate d = pt.getDepartureDate() != null ? pt.getDepartureDate() : (stopDep != null ? stopDep.toLocalDate() : (tripDep != null ? tripDep.toLocalDate() : schedule.getTravelDate()));
                        ptDep = LocalDateTime.of(d, t);
                        pt.setDepartureDate(d);
                        pt.setDepartureDateTime(ptDep);
                    }

                    if (ptArr != null && ptDep != null) {
                        if (ptDep.isBefore(ptArr)) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    String.format("Point '%s' departure (%s) cannot precede its arrival (%s).",
                                            pt.getPointName(), ptDep, ptArr));
                        }
                        long ptHalt = java.time.Duration.between(ptArr, ptDep).toMinutes();
                        pt.setHaltMinutes((int) ptHalt);
                    }

                    // Bounds check against complete trip
                    if (tripDep != null) {
                        if (ptArr != null && ptArr.isBefore(tripDep)) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    String.format("Point '%s' arrival (%s) cannot precede trip departure (%s).",
                                            pt.getPointName(), ptArr, tripDep));
                        }
                        if (ptDep != null && ptDep.isBefore(tripDep)) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    String.format("Point '%s' departure (%s) cannot precede trip departure (%s).",
                                            pt.getPointName(), ptDep, tripDep));
                        }
                    }
                    if (tripArr != null) {
                        if (ptArr != null && ptArr.isAfter(tripArr)) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    String.format("Point '%s' arrival (%s) cannot exceed trip arrival (%s).",
                                            pt.getPointName(), ptArr, tripArr));
                        }
                        if (ptDep != null && ptDep.isAfter(tripArr)) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    String.format("Point '%s' departure (%s) cannot exceed trip arrival (%s).",
                                            pt.getPointName(), ptDep, tripArr));
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks whether the bus has any overlapping active trips within [candDep, candArr + buffer).
     */
    public void checkBusAvailability(Bus bus, BusSchedule schedule) {
        LocalDateTime candDep = schedule.getDepartureDateTime();
        LocalDateTime candArr = schedule.getArrivalDateTime();
        LocalDateTime candEndWithBuf = candArr.plusMinutes(turnaroundBufferMinutes);

        List<BusSchedule> activeSchedules = scheduleRepository.findActiveSchedulesByBusId(bus.getId());

        for (BusSchedule existing : activeSchedules) {
            if (existing.getId() != null && existing.getId().equals(schedule.getId())) {
                continue;
            }

            if (existing.getStatus() != null && "CANCELLED".equalsIgnoreCase(existing.getStatus().trim())) {
                continue;
            }

            LocalDateTime existingDep = existing.getEffectiveDepartureDateTime();
            LocalDateTime existingArr = existing.getEffectiveArrivalDateTime();

            if (existingDep == null || existingArr == null) {
                continue;
            }

            LocalDateTime existingEndWithBuf = existingArr.plusMinutes(turnaroundBufferMinutes);

            // Two intervals [D1, End1) and [D2, End2) overlap iff D1 < End2 AND D2 < End1
            boolean overlaps = candDep.isBefore(existingEndWithBuf) && existingDep.isBefore(candEndWithBuf);

            if (overlaps) {
                log.warn("Bus scheduling conflict on Bus {} (ID: {}). Proposed [{} to {}] overlaps existing schedule #{} [{} to {}] (turnaround buffer {} min)",
                        bus.getBusNumber(), bus.getId(), candDep, candEndWithBuf,
                        existing.getId(), existingDep, existingEndWithBuf, turnaroundBufferMinutes);

                throw new ResponseStatusException(HttpStatus.CONFLICT, String.format(
                        "Bus %s (ID: %d) is already scheduled during this operating window. " +
                        "Conflicting schedule #%d (%s to %s) operates from %s to %s (including %d min turnaround buffer). " +
                        "Proposed schedule operates from %s to %s.",
                        bus.getBusNumber(), bus.getId(),
                        existing.getId(), existing.getOrigin(), existing.getDestination(),
                        existingDep, existingEndWithBuf, turnaroundBufferMinutes,
                        candDep, candEndWithBuf
                ));
            }
        }
    }

    /**
     * Non-destructive pre-validation conflict check for wizard (unrestricted/internal overload).
     */
    public ScheduleConflictCheckResponse checkConflict(ScheduleConflictCheckRequest request) {
        return checkConflict(null, true, request);
    }

    /**
     * Non-destructive pre-validation conflict check with caller authorization, bus access enforcement,
     * past departure validation, and cross-midnight / turnaround buffer interval overlap checking.
     */
    public ScheduleConflictCheckResponse checkConflict(Long callerUserId, boolean isAdmin, ScheduleConflictCheckRequest request) {
        if (request == null || request.getBusId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "busId is required");
        }
        Bus bus = busRepository.findById(request.getBusId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bus not found with id: " + request.getBusId()));

        if (!isAdmin && (bus.getOperatorId() == null || !bus.getOperatorId().equals(callerUserId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this bus");
        }

        if (request.getTravelDate() == null || request.getDepartureTime() == null || request.getDepartureTime().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "travelDate and departureTime are required");
        }

        LocalTime depTime = parseAndValidateTime(request.getDepartureTime(), "departureTime");
        LocalDateTime candDep = LocalDateTime.of(request.getTravelDate(), depTime);

        // Departure time cannot be in the past
        LocalDateTime now = LocalDateTime.now(this.clock);
        if (candDep.isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Departure time cannot be in the past. Attempted departure: " + candDep + ", current time: " + now);
        }

        LocalDate arrDate = request.getArrivalDate();
        LocalTime arrTime = null;
        if (request.getArrivalTime() != null && !request.getArrivalTime().trim().isEmpty()) {
            arrTime = parseAndValidateTime(request.getArrivalTime(), "arrivalTime");
        }

        if (arrTime == null) {
            arrTime = depTime.plusHours(4);
            if (arrDate == null) {
                arrDate = request.getTravelDate();
                if (arrTime.isBefore(depTime)) {
                    arrDate = arrDate.plusDays(1);
                }
            }
        } else {
            if (arrDate == null) {
                arrDate = request.getTravelDate();
                if (arrTime.isBefore(depTime)) {
                    arrDate = arrDate.plusDays(1);
                }
            }
        }

        LocalDateTime candArr = LocalDateTime.of(arrDate, arrTime);

        if (!candArr.isAfter(candDep)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Arrival date-time (" + candArr + ") must be strictly after departure date-time (" + candDep + ").");
        }

        LocalDateTime candEndWithBuf = candArr.plusMinutes(turnaroundBufferMinutes);

        List<BusSchedule> activeSchedules = scheduleRepository.findActiveSchedulesByBusId(bus.getId());

        for (BusSchedule existing : activeSchedules) {
            if (request.getExcludeScheduleId() != null && request.getExcludeScheduleId().equals(existing.getId())) {
                continue;
            }
            if (existing.getStatus() != null && "CANCELLED".equalsIgnoreCase(existing.getStatus().trim())) {
                continue;
            }

            LocalDateTime existingDep = existing.getEffectiveDepartureDateTime();
            LocalDateTime existingArr = existing.getEffectiveArrivalDateTime();

            if (existingDep == null || existingArr == null) {
                continue;
            }

            LocalDateTime existingEndWithBuf = existingArr.plusMinutes(turnaroundBufferMinutes);

            boolean overlaps = candDep.isBefore(existingEndWithBuf) && existingDep.isBefore(candEndWithBuf);

            if (overlaps) {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
                String msg = String.format(
                        "This bus already has a schedule from %s to %s on %s to %s. Please choose a different departure date/time or assign a different bus.",
                        existing.getOrigin() != null ? existing.getOrigin() : "Origin",
                        existing.getDestination() != null ? existing.getDestination() : "Destination",
                        existingDep.format(fmt),
                        existingArr.format(fmt)
                );

                return ScheduleConflictCheckResponse.builder()
                        .hasConflict(true)
                        .conflictingScheduleId(existing.getId())
                        .busId(bus.getId())
                        .busNumber(bus.getBusNumber())
                        .busName(bus.getBusName())
                        .origin(existing.getOrigin())
                        .destination(existing.getDestination())
                        .existingDeparture(existingDep.toString())
                        .existingArrival(existingArr.toString())
                        .existingDepartureFormatted(existingDep.format(fmt))
                        .existingArrivalFormatted(existingArr.format(fmt))
                        .turnaroundBufferMinutes(turnaroundBufferMinutes)
                        .message(msg)
                        .build();
            }
        }

        return ScheduleConflictCheckResponse.builder()
                .hasConflict(false)
                .turnaroundBufferMinutes(turnaroundBufferMinutes)
                .build();
    }

    /**
     * Validates route ownership, active status, stop configurations, and snapshots stops/points.
     */
    private void validateAndSnapshotRoute(Long callerUserId, boolean isAdmin, BusSchedule schedule) {
        if (schedule.getRouteId() == null) {
            return;
        }

        Route route = routeRepository.findById(schedule.getRouteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route not found with id: " + schedule.getRouteId()));

        if (!isAdmin && (route.getOperatorId() == null || !route.getOperatorId().equals(callerUserId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this route");
        }

        if ("INACTIVE".equalsIgnoreCase(route.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot schedule trips on an inactive route.");
        }

        if (schedule.getOrigin() == null || schedule.getOrigin().trim().isEmpty()) {
            schedule.setOrigin(route.getOrigin());
        }
        if (schedule.getDestination() == null || schedule.getDestination().trim().isEmpty()) {
            schedule.setDestination(route.getDestination());
        }

        List<RouteStop> rStops = routeStopRepository.findByRouteIdOrderByStopOrderAsc(route.getId());
        if (rStops.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route has invalid stop configuration.");
        }

        // Snapshot stops if not provided by caller
        if (schedule.getStops() == null || schedule.getStops().isEmpty()) {
            List<ScheduleStopDto> snapshotStops = new ArrayList<>();
            double defFare = (schedule.getFare() != null && rStops.size() > 1)
                    ? Math.round((schedule.getFare() / (rStops.size() - 1)) * 100.0) / 100.0
                    : 100.0;
            for (int i = 0; i < rStops.size(); i++) {
                RouteStop rs = rStops.get(i);
                ScheduleStopDto dto = new ScheduleStopDto();
                dto.setStopOrder(rs.getStopOrder());
                dto.setStopName(rs.getStopName());
                dto.setStopType(rs.getStopType());
                dto.setDistanceKm(rs.getDistanceKm());
                dto.setConsecutiveFare(i < rStops.size() - 1 ? defFare : 0.0);
                if (i == 0) {
                    dto.setDepartureTime(schedule.getDepartureTime());
                } else if (i == rStops.size() - 1) {
                    dto.setArrivalTime(schedule.getArrivalTime());
                }
                snapshotStops.add(dto);
            }
            schedule.setStops(snapshotStops);
        } else {
            // If caller provided stops, validate that stop names and sequence match route
            if (schedule.getStops().size() != rStops.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Schedule stop count (" + schedule.getStops().size() + ") does not match route stop count (" + rStops.size() + ").");
            }
            for (int i = 0; i < rStops.size(); i++) {
                String provided = schedule.getStops().get(i).getStopName();
                String expected = rStops.get(i).getStopName();
                if (!isStopNameMatch(provided, expected)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Schedule stop mismatch at position " + i + ": expected '" + expected + "' from route but received '" + provided + "'.");
                }
                // Align stop name to canonical route stop name
                schedule.getStops().get(i).setStopName(expected);
            }
        }

        // Auto-populate boarding and dropping points summary from route if not provided
        if (schedule.getBoardingPoints() == null || schedule.getBoardingPoints().trim().isEmpty()) {
            List<RoutePoint> bps = routePointRepository.findByRouteId(route.getId()).stream()
                    .filter(p -> "BOARDING".equalsIgnoreCase(p.getPointType()) && Boolean.TRUE.equals(p.getActive()))
                    .toList();
            if (!bps.isEmpty()) {
                schedule.setBoardingPoints(String.join(", ", bps.stream().map(RoutePoint::getPointName).toList()));
            }
        }
        if (schedule.getDroppingPoints() == null || schedule.getDroppingPoints().trim().isEmpty()) {
            List<RoutePoint> dps = routePointRepository.findByRouteId(route.getId()).stream()
                    .filter(p -> "DROPPING".equalsIgnoreCase(p.getPointType()) && Boolean.TRUE.equals(p.getActive()))
                    .toList();
            if (!dps.isEmpty()) {
                schedule.setDroppingPoints(String.join(", ", dps.stream().map(RoutePoint::getPointName).toList()));
            }
        }
    }

    private LocalTime parseAndValidateTime(String timeStr, String fieldName) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        String trimmed = timeStr.trim();
        if (!TIME_PATTERN.matcher(trimmed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid time format for " + fieldName + ": '" + timeStr + "'. Expected HH:mm in 24-hour format (e.g. 08:30, 21:45).");
        }
        try {
            return LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid time value for " + fieldName + ": '" + timeStr + "'.");
        }
    }

    private boolean isStopNameMatch(String provided, String expected) {
        if (provided == null || expected == null) {
            return false;
        }
        String p = provided.trim();
        String e = expected.trim();
        if (p.equalsIgnoreCase(e)) {
            return true;
        }
        // Normalize display suffixes: e.g. "Bengaluru (Start)", "Bengaluru (Origin)", "Hyderabad (End)", "Hyderabad (Destination)"
        String pClean = p.replaceAll("(?i)\\s*\\((Start|End|Origin|Destination)\\)", "").trim();
        String eClean = e.replaceAll("(?i)\\s*\\((Start|End|Origin|Destination)\\)", "").trim();
        return pClean.equalsIgnoreCase(eClean);
    }
}
