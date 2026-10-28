package com.traveleasy.bus.service;

import com.traveleasy.bus.dto.CreateRouteRequest;
import com.traveleasy.bus.dto.RouteDto;
import com.traveleasy.bus.dto.RoutePointDto;
import com.traveleasy.bus.dto.RouteStopDto;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.Route;
import com.traveleasy.bus.model.RoutePoint;
import com.traveleasy.bus.model.RouteStop;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.RoutePointRepository;
import com.traveleasy.bus.repository.RouteRepository;
import com.traveleasy.bus.repository.RouteStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final RoutePointRepository routePointRepository;
    private final BusScheduleRepository scheduleRepository;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a unique route code e.g. "RT-A8F2K9"
     */
    public String generateUniqueRouteCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder sb = new StringBuilder("RT-");
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!routeRepository.existsByRouteCode(code)) {
                return code;
            }
        }
        return "RT-" + System.currentTimeMillis();
    }

    /**
     * Validates route details, stop ordering, physical location uniqueness, and boarding/dropping points.
     */
    public void validateRouteDefinition(CreateRouteRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route request payload cannot be empty.");
        }

        if (req.getRouteName() == null || req.getRouteName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route name is required.");
        }

        if (req.getOrigin() == null || req.getOrigin().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origin city is required.");
        }

        if (req.getDestination() == null || req.getDestination().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination city is required.");
        }

        String normOrigin = req.getOrigin().trim();
        String normDestination = req.getDestination().trim();

        if (normOrigin.equalsIgnoreCase(normDestination)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origin and destination cannot be the same physical location.");
        }

        if (req.getStatus() != null && !req.getStatus().trim().isEmpty()) {
            String s = req.getStatus().trim().toUpperCase();
            if (!s.equals("ACTIVE") && !s.equals("DRAFT") && !s.equals("INACTIVE")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid route status '" + req.getStatus() + "'. Allowed values: ACTIVE, DRAFT, INACTIVE.");
            }
        }

        List<RouteStopDto> stops = req.getStops();
        if (stops == null || stops.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A route must contain at least 2 stops: origin and destination.");
        }

        Set<String> seenLocations = new HashSet<>();
        int lastIndex = stops.size() - 1;

        for (int i = 0; i <= lastIndex; i++) {
            RouteStopDto stop = stops.get(i);
            if (stop.getStopName() == null || stop.getStopName().trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stop name at position " + i + " cannot be blank.");
            }

            String stopName = stop.getStopName().trim();
            String normStop = stopName.toLowerCase();

            // Constraint: Repeated physical locations are disallowed within a route
            if (seenLocations.contains(normStop)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Repeated physical location disallowed on route: '" + stopName + "' appears more than once.");
            }
            seenLocations.add(normStop);

            // Sequence validation
            if (stop.getStopOrder() != null && !stop.getStopOrder().equals(i)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Stop order mismatch at position " + i + ": expected " + i + " but found " + stop.getStopOrder());
            }

            // Stop 0 must match origin
            if (i == 0 && !stopName.equalsIgnoreCase(normOrigin)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "First stop '" + stopName + "' must match origin '" + normOrigin + "'.");
            }

            // Stop lastIndex must match destination
            if (i == lastIndex && !stopName.equalsIgnoreCase(normDestination)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Final stop '" + stopName + "' must match destination '" + normDestination + "'.");
            }

            // Boarding / Dropping point validation
            List<RoutePointDto> boardings = stop.getBoardingPoints();
            List<RoutePointDto> droppings = stop.getDroppingPoints();

            // Origin must have at least 1 boarding point
            if (i == 0 && (boardings == null || boardings.stream().noneMatch(p -> p.getPointName() != null && !p.getPointName().trim().isEmpty()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Origin stop '" + stopName + "' must configure at least one valid boarding point.");
            }

            // Destination must have at least 1 dropping point
            if (i == lastIndex && (droppings == null || droppings.stream().noneMatch(p -> p.getPointName() != null && !p.getPointName().trim().isEmpty()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Destination stop '" + stopName + "' must configure at least one valid dropping point.");
            }

            // Check non-blank point names
            if (boardings != null) {
                for (RoutePointDto bp : boardings) {
                    if (bp.getPointName() == null || bp.getPointName().trim().isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Boarding point name at stop '" + stopName + "' cannot be blank.");
                    }
                }
            }
            if (droppings != null) {
                for (RoutePointDto dp : droppings) {
                    if (dp.getPointName() == null || dp.getPointName().trim().isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dropping point name at stop '" + stopName + "' cannot be blank.");
                    }
                }
            }
        }
    }

    /**
     * Atomically creates a new Route, its ordered RouteStops, and its RoutePoints.
     */
    @Transactional
    public RouteDto createRoute(Long operatorId, CreateRouteRequest req) {
        validateRouteDefinition(req);

        String routeCode = (req.getRouteCode() != null && !req.getRouteCode().trim().isEmpty())
                ? req.getRouteCode().trim().toUpperCase()
                : generateUniqueRouteCode();

        if (routeRepository.existsByRouteCode(routeCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Route code '" + routeCode + "' is already in use.");
        }

        Route route = Route.builder()
                .operatorId(operatorId)
                .routeCode(routeCode)
                .routeName(req.getRouteName().trim())
                .origin(req.getOrigin().trim())
                .destination(req.getDestination().trim())
                .description(req.getDescription() != null ? req.getDescription().trim() : null)
                .status((req.getStatus() != null && !req.getStatus().trim().isEmpty()) ? req.getStatus().trim().toUpperCase() : "ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Route savedRoute = routeRepository.save(route);

        List<RouteStopDto> savedStopDtos = new ArrayList<>();
        List<RouteStopDto> inputStops = req.getStops();
        int lastIndex = inputStops.size() - 1;

        for (int i = 0; i <= lastIndex; i++) {
            RouteStopDto stopDto = inputStops.get(i);
            String stopType = (i == 0) ? "ORIGIN" : (i == lastIndex ? "DESTINATION" : "INTERMEDIATE");

            RouteStop stop = RouteStop.builder()
                    .routeId(savedRoute.getId())
                    .stopOrder(i)
                    .stopName(stopDto.getStopName().trim())
                    .stopType(stopType)
                    .distanceKm(stopDto.getDistanceKm())
                    .createdAt(LocalDateTime.now())
                    .build();

            RouteStop savedStop = routeStopRepository.save(stop);

            List<RoutePointDto> savedBoardingDtos = new ArrayList<>();
            if (stopDto.getBoardingPoints() != null) {
                for (RoutePointDto bpDto : stopDto.getBoardingPoints()) {
                    if (bpDto.getPointName() != null && !bpDto.getPointName().trim().isEmpty()) {
                        RoutePoint bp = RoutePoint.builder()
                                .routeId(savedRoute.getId())
                                .routeStopId(savedStop.getId())
                                .pointName(bpDto.getPointName().trim())
                                .pointType("BOARDING")
                                .address(bpDto.getAddress() != null ? bpDto.getAddress().trim() : null)
                                .timeSlot(bpDto.getTimeSlot() != null ? bpDto.getTimeSlot().trim() : null)
                                .active(bpDto.getActive() != null ? bpDto.getActive() : true)
                                .createdAt(LocalDateTime.now())
                                .build();
                        RoutePoint savedBp = routePointRepository.save(bp);
                        savedBoardingDtos.add(mapToPointDto(savedBp));
                    }
                }
            }

            List<RoutePointDto> savedDroppingDtos = new ArrayList<>();
            if (stopDto.getDroppingPoints() != null) {
                for (RoutePointDto dpDto : stopDto.getDroppingPoints()) {
                    if (dpDto.getPointName() != null && !dpDto.getPointName().trim().isEmpty()) {
                        RoutePoint dp = RoutePoint.builder()
                                .routeId(savedRoute.getId())
                                .routeStopId(savedStop.getId())
                                .pointName(dpDto.getPointName().trim())
                                .pointType("DROPPING")
                                .address(dpDto.getAddress() != null ? dpDto.getAddress().trim() : null)
                                .timeSlot(dpDto.getTimeSlot() != null ? dpDto.getTimeSlot().trim() : null)
                                .active(dpDto.getActive() != null ? dpDto.getActive() : true)
                                .createdAt(LocalDateTime.now())
                                .build();
                        RoutePoint savedDp = routePointRepository.save(dp);
                        savedDroppingDtos.add(mapToPointDto(savedDp));
                    }
                }
            }

            RouteStopDto savedStopDto = RouteStopDto.builder()
                    .id(savedStop.getId())
                    .routeId(savedRoute.getId())
                    .stopOrder(savedStop.getStopOrder())
                    .stopName(savedStop.getStopName())
                    .stopType(savedStop.getStopType())
                    .distanceKm(savedStop.getDistanceKm())
                    .boardingPoints(savedBoardingDtos)
                    .droppingPoints(savedDroppingDtos)
                    .build();

            savedStopDtos.add(savedStopDto);
        }

        return RouteDto.builder()
                .id(savedRoute.getId())
                .operatorId(savedRoute.getOperatorId())
                .routeCode(savedRoute.getRouteCode())
                .routeName(savedRoute.getRouteName())
                .origin(savedRoute.getOrigin())
                .destination(savedRoute.getDestination())
                .description(savedRoute.getDescription())
                .status(savedRoute.getStatus())
                .createdAt(savedRoute.getCreatedAt())
                .updatedAt(savedRoute.getUpdatedAt())
                .stops(savedStopDtos)
                .scheduleCount(0)
                .build();
    }

    /**
     * Lists all routes owned by the operator with full stop/point details and schedule counts.
     */
    @Transactional(readOnly = true)
    public List<RouteDto> getOperatorRoutes(Long operatorId) {
        List<Route> routes = routeRepository.findByOperatorIdOrderByCreatedAtDesc(operatorId);
        List<RouteDto> result = new ArrayList<>();

        for (Route r : routes) {
            result.add(populateRouteDto(r));
        }
        return result;
    }

    /**
     * Retrieves full route details with ownership verification.
     */
    @Transactional(readOnly = true)
    public RouteDto getRouteDetails(Long routeId, Long callerUserId, boolean isAdmin) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found with id: " + routeId));

        if (!isAdmin && !route.getOperatorId().equals(callerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this route.");
        }

        return populateRouteDto(route);
    }

    /**
     * Updates an existing Route.
     * Enforces the confirmed structural edit policy:
     * Structural edits (adding/removing/reordering stops or changing stop locations)
     * are strictly blocked while future active schedules depend on the route (409 Conflict).
     */
    @Transactional
    public RouteDto updateRoute(Long routeId, Long callerUserId, boolean isAdmin, CreateRouteRequest req) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found with id: " + routeId));

        if (!isAdmin && !route.getOperatorId().equals(callerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this route.");
        }

        validateRouteDefinition(req);

        List<RouteStop> existingStops = routeStopRepository.findByRouteIdOrderByStopOrderAsc(routeId);
        boolean isStructuralChange = isStructuralChange(existingStops, req);

        if (isStructuralChange) {
            LocalDate today = LocalDate.now();
            List<BusSchedule> futureSchedules = scheduleRepository.findActiveUpcomingSchedulesByRouteId(routeId, today);
            if (!futureSchedules.isEmpty()) {
                int count = futureSchedules.size();
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Structural edit blocked: " + count + " active upcoming schedule(s) depend on this route. " +
                        "Resolve dependent schedules before modifying stop order, adding/removing stops, or changing physical endpoints.");
            }
        }

        // Apply metadata updates
        route.setRouteName(req.getRouteName().trim());
        route.setOrigin(req.getOrigin().trim());
        route.setDestination(req.getDestination().trim());
        route.setDescription(req.getDescription() != null ? req.getDescription().trim() : null);
        if (req.getStatus() != null && !req.getStatus().trim().isEmpty()) {
            route.setStatus(req.getStatus().trim().toUpperCase());
        }
        route.setUpdatedAt(LocalDateTime.now());
        Route savedRoute = routeRepository.save(route);

        // Atomically replace stops and points
        routePointRepository.deleteByRouteId(routeId);
        routePointRepository.flush();
        routeStopRepository.deleteByRouteId(routeId);
        routeStopRepository.flush();

        List<RouteStopDto> savedStopDtos = new ArrayList<>();
        List<RouteStopDto> inputStops = req.getStops();
        int lastIndex = inputStops.size() - 1;

        for (int i = 0; i <= lastIndex; i++) {
            RouteStopDto stopDto = inputStops.get(i);
            String stopType = (i == 0) ? "ORIGIN" : (i == lastIndex ? "DESTINATION" : "INTERMEDIATE");

            RouteStop stop = RouteStop.builder()
                    .routeId(savedRoute.getId())
                    .stopOrder(i)
                    .stopName(stopDto.getStopName().trim())
                    .stopType(stopType)
                    .distanceKm(stopDto.getDistanceKm())
                    .createdAt(LocalDateTime.now())
                    .build();

            RouteStop savedStop = routeStopRepository.save(stop);

            List<RoutePointDto> savedBoardingDtos = new ArrayList<>();
            if (stopDto.getBoardingPoints() != null) {
                for (RoutePointDto bpDto : stopDto.getBoardingPoints()) {
                    if (bpDto.getPointName() != null && !bpDto.getPointName().trim().isEmpty()) {
                        RoutePoint bp = RoutePoint.builder()
                                .routeId(savedRoute.getId())
                                .routeStopId(savedStop.getId())
                                .pointName(bpDto.getPointName().trim())
                                .pointType("BOARDING")
                                .address(bpDto.getAddress() != null ? bpDto.getAddress().trim() : null)
                                .timeSlot(bpDto.getTimeSlot() != null ? bpDto.getTimeSlot().trim() : null)
                                .active(bpDto.getActive() != null ? bpDto.getActive() : true)
                                .createdAt(LocalDateTime.now())
                                .build();
                        RoutePoint savedBp = routePointRepository.save(bp);
                        savedBoardingDtos.add(mapToPointDto(savedBp));
                    }
                }
            }

            List<RoutePointDto> savedDroppingDtos = new ArrayList<>();
            if (stopDto.getDroppingPoints() != null) {
                for (RoutePointDto dpDto : stopDto.getDroppingPoints()) {
                    if (dpDto.getPointName() != null && !dpDto.getPointName().trim().isEmpty()) {
                        RoutePoint dp = RoutePoint.builder()
                                .routeId(savedRoute.getId())
                                .routeStopId(savedStop.getId())
                                .pointName(dpDto.getPointName().trim())
                                .pointType("DROPPING")
                                .address(dpDto.getAddress() != null ? dpDto.getAddress().trim() : null)
                                .timeSlot(dpDto.getTimeSlot() != null ? dpDto.getTimeSlot().trim() : null)
                                .active(dpDto.getActive() != null ? dpDto.getActive() : true)
                                .createdAt(LocalDateTime.now())
                                .build();
                        RoutePoint savedDp = routePointRepository.save(dp);
                        savedDroppingDtos.add(mapToPointDto(savedDp));
                    }
                }
            }

            RouteStopDto savedStopDto = RouteStopDto.builder()
                    .id(savedStop.getId())
                    .routeId(savedRoute.getId())
                    .stopOrder(savedStop.getStopOrder())
                    .stopName(savedStop.getStopName())
                    .stopType(savedStop.getStopType())
                    .distanceKm(savedStop.getDistanceKm())
                    .boardingPoints(savedBoardingDtos)
                    .droppingPoints(savedDroppingDtos)
                    .build();

            savedStopDtos.add(savedStopDto);
        }

        int schedCount = (int) scheduleRepository.countByRouteId(routeId);

        return RouteDto.builder()
                .id(savedRoute.getId())
                .operatorId(savedRoute.getOperatorId())
                .routeCode(savedRoute.getRouteCode())
                .routeName(savedRoute.getRouteName())
                .origin(savedRoute.getOrigin())
                .destination(savedRoute.getDestination())
                .description(savedRoute.getDescription())
                .status(savedRoute.getStatus())
                .createdAt(savedRoute.getCreatedAt())
                .updatedAt(savedRoute.getUpdatedAt())
                .stops(savedStopDtos)
                .scheduleCount(schedCount)
                .build();
    }

    /**
     * Checks whether an edit constitutes a structural change.
     */
    private boolean isStructuralChange(List<RouteStop> existingStops, CreateRouteRequest req) {
        List<RouteStopDto> newStops = req.getStops();
        if (existingStops == null || newStops == null) return true;
        if (existingStops.size() != newStops.size()) return true;

        for (int i = 0; i < existingStops.size(); i++) {
            RouteStop ex = existingStops.get(i);
            RouteStopDto nw = newStops.get(i);
            if (!ex.getStopName().trim().equalsIgnoreCase(nw.getStopName().trim())) {
                return true;
            }
            if (ex.getStopOrder() != i) {
                return true;
            }
        }
        return false;
    }

    /**
     * Soft activation / deactivation of a route.
     * Enforces future-schedule dependency guard on deactivation.
     */
    @Transactional
    public RouteDto changeRouteStatus(Long routeId, Long callerUserId, boolean isAdmin, String newStatus) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found with id: " + routeId));

        if (!isAdmin && !route.getOperatorId().equals(callerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this route.");
        }

        String targetStatus = newStatus.trim().toUpperCase();
        if ("INACTIVE".equals(targetStatus)) {
            LocalDate today = LocalDate.now();
            List<BusSchedule> futureSchedules = scheduleRepository.findActiveUpcomingSchedulesByRouteId(routeId, today);
            if (!futureSchedules.isEmpty()) {
                int count = futureSchedules.size();
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cannot deactivate route: " + count + " active upcoming schedule(s) depend on this route.");
            }
        }

        route.setStatus(targetStatus);
        route.setUpdatedAt(LocalDateTime.now());
        Route saved = routeRepository.save(route);
        return populateRouteDto(saved);
    }

    /**
     * Returns schedules linked to this route.
     */
    @Transactional(readOnly = true)
    public List<BusSchedule> getRouteSchedules(Long routeId, Long callerUserId, boolean isAdmin) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found with id: " + routeId));

        if (!isAdmin && !route.getOperatorId().equals(callerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this route.");
        }

        return scheduleRepository.findByRouteIdOrderByTravelDateDesc(routeId);
    }

    /**
     * Helper to fully populate RouteDto with stops and points.
     */
    private RouteDto populateRouteDto(Route route) {
        List<RouteStop> stops = routeStopRepository.findByRouteIdOrderByStopOrderAsc(route.getId());
        List<RoutePoint> points = routePointRepository.findByRouteId(route.getId());

        Map<Long, List<RoutePointDto>> boardingMap = new HashMap<>();
        Map<Long, List<RoutePointDto>> droppingMap = new HashMap<>();

        for (RoutePoint p : points) {
            RoutePointDto pDto = mapToPointDto(p);
            if ("BOARDING".equalsIgnoreCase(p.getPointType())) {
                boardingMap.computeIfAbsent(p.getRouteStopId(), k -> new ArrayList<>()).add(pDto);
            } else {
                droppingMap.computeIfAbsent(p.getRouteStopId(), k -> new ArrayList<>()).add(pDto);
            }
        }

        List<RouteStopDto> stopDtos = new ArrayList<>();
        for (RouteStop s : stops) {
            stopDtos.add(RouteStopDto.builder()
                    .id(s.getId())
                    .routeId(s.getRouteId())
                    .stopOrder(s.getStopOrder())
                    .stopName(s.getStopName())
                    .stopType(s.getStopType())
                    .distanceKm(s.getDistanceKm())
                    .boardingPoints(boardingMap.getOrDefault(s.getId(), new ArrayList<>()))
                    .droppingPoints(droppingMap.getOrDefault(s.getId(), new ArrayList<>()))
                    .build());
        }

        int count = (int) scheduleRepository.countByRouteId(route.getId());

        return RouteDto.builder()
                .id(route.getId())
                .operatorId(route.getOperatorId())
                .routeCode(route.getRouteCode())
                .routeName(route.getRouteName())
                .origin(route.getOrigin())
                .destination(route.getDestination())
                .description(route.getDescription())
                .status(route.getStatus())
                .createdAt(route.getCreatedAt())
                .updatedAt(route.getUpdatedAt())
                .stops(stopDtos)
                .scheduleCount(count)
                .build();
    }

    private RoutePointDto mapToPointDto(RoutePoint p) {
        return RoutePointDto.builder()
                .id(p.getId())
                .routeId(p.getRouteId())
                .routeStopId(p.getRouteStopId())
                .pointName(p.getPointName())
                .pointType(p.getPointType())
                .address(p.getAddress())
                .timeSlot(p.getTimeSlot())
                .active(p.getActive())
                .build();
    }
}
