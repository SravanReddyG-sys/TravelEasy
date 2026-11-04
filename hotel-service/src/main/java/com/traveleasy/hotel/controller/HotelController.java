package com.traveleasy.hotel.controller;

import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import com.traveleasy.hotel.security.HotelSecurityService;
import com.traveleasy.hotel.security.HotelSecurityService.CallerPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelPropertyRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final HotelSecurityService securityService;

    /**
     * Register a new hotel property.
     * Restricted to ROLE_HOTEL_MANAGER and ROLE_ADMIN.
     * The manager ID and manager Name are derived authoritatively from the JWT token.
     * Per BD-03, newly registered properties immediately receive ACTIVE status.
     */
    @PostMapping
    public ResponseEntity<?> addHotel(
            @RequestBody HotelProperty hotel,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to register hotel property"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        if (principal == null || !principal.hasManagerOrAdminAccess()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Hotel Manager or Admin role required"));
        }

        if (principal.userId() == null || principal.userId() <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
        }

        // Authoritative assignment: Managers can only register properties for themselves
        if (principal.isHotelManager()) {
            hotel.setManagerId(principal.userId());
            hotel.setManagerName(principal.fullName());
        } else if (principal.isAdmin() && (hotel.getManagerId() == null || hotel.getManagerId() <= 0)) {
            hotel.setManagerId(principal.userId());
            hotel.setManagerName(principal.fullName());
        }

        // BD-03: Newly registered properties receive ACTIVE status
        hotel.setStatus("ACTIVE");

        HotelProperty saved = hotelRepository.save(hotel);
        log.info("Hotel property '{}' (ID: {}) registered by user ID: {}", saved.getName(), saved.getId(), principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Retrieve all hotels (public traveller search / discovery).
     */
    @GetMapping
    public ResponseEntity<List<HotelProperty>> getAllHotels() {
        return ResponseEntity.ok(hotelRepository.findAll());
    }

    /**
     * Retrieve hotels managed by a specific manager.
     * Restricted to ROLE_HOTEL_MANAGER (own properties only) and ROLE_ADMIN (all properties).
     */
    @GetMapping("/manager/{managerId}")
    public ResponseEntity<?> getHotelsByManager(
            @PathVariable Long managerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to view manager properties"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        if (principal == null || !principal.hasManagerOrAdminAccess()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Hotel Manager or Admin role required"));
        }

        // Ownership enforcement: Manager cannot view another manager's properties
        if (principal.isHotelManager() && !principal.userId().equals(managerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Cannot access properties belonging to another hotel manager"));
        }

        List<HotelProperty> hotels = hotelRepository.findByManagerId(managerId);
        return ResponseEntity.ok(hotels);
    }

    /**
     * Public hotel search by destination city.
     * Open to all users without authentication.
     */
    @GetMapping("/search")
    public ResponseEntity<List<HotelProperty>> searchHotels(
            @RequestParam String city,
            @RequestParam(required = false) String checkIn,
            @RequestParam(required = false) String checkOut,
            @RequestParam(required = false, defaultValue = "1") Integer guests) {
        return ResponseEntity.ok(hotelRepository.findByCityIgnoreCase(city));
    }

    /**
     * Public hotel details lookup including available room types.
     * Open to all users without authentication.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getHotelDetails(@PathVariable Long id) {
        HotelProperty hotel = hotelRepository.findById(id).orElse(null);
        if (hotel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Hotel property not found"));
        }
        List<RoomType> rooms = roomTypeRepository.findByHotelId(id);
        return ResponseEntity.ok(Map.of("hotel", hotel, "rooms", rooms));
    }

    /**
     * Add a room type to an existing hotel property.
     * Restricted to ROLE_HOTEL_MANAGER (own properties only) and ROLE_ADMIN.
     * Room ownership is enforced through the parent hotel property.
     */
    @PostMapping("/{hotelId}/rooms")
    public ResponseEntity<?> addRoomType(
            @PathVariable Long hotelId,
            @RequestBody RoomType roomType,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to add room category"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        if (principal == null || !principal.hasManagerOrAdminAccess()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Hotel Manager or Admin role required"));
        }

        Optional<HotelProperty> hotelOpt = hotelRepository.findById(hotelId);
        if (hotelOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Hotel property not found"));
        }

        HotelProperty hotel = hotelOpt.get();

        // Property & Room ownership enforcement
        if (principal.isHotelManager() && !principal.userId().equals(hotel.getManagerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Cannot add room categories to a property you do not own"));
        }

        roomType.setHotelId(hotelId);
        if (roomType.getAvailableRooms() == null) {
            roomType.setAvailableRooms(roomType.getTotalRooms());
        }

        RoomType saved = roomTypeRepository.save(roomType);
        log.info("Room category '{}' (ID: {}) added to hotel ID: {} by user ID: {}",
                saved.getRoomTypeName(), saved.getId(), hotelId, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Reserve rooms for a booking.
     * Sensitive inventory operation:
     * - Accessible by authenticated travellers / customers, admins, OR
     * - Trusted internal services (e.g. booking-service).
     * Unauthenticated callers are rejected.
     */
    @PutMapping("/rooms/{roomId}/reserve")
    public ResponseEntity<?> reserveRooms(
            @PathVariable Long roomId,
            @RequestParam Integer count,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        boolean isInternal = securityService.isInternalService(internalService);

        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to reserve room inventory"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }
        }

        if (count == null || count <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Room reservation count must be at least 1"));
        }

        RoomType roomType = roomTypeRepository.findById(roomId).orElse(null);
        if (roomType == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Room category not found"));
        }

        // BD-03 / BD-08: Check if parent hotel is active
        if (roomType.getHotelId() != null) {
            Optional<HotelProperty> hotelOpt = hotelRepository.findById(roomType.getHotelId());
            if (hotelOpt.isPresent() && "INACTIVE".equalsIgnoreCase(hotelOpt.get().getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Hotel property is inactive and cannot accept reservations"));
            }
        }

        if (roomType.getAvailableRooms() == null || roomType.getAvailableRooms() < count) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Insufficient room inventory available"));
        }

        roomType.setAvailableRooms(roomType.getAvailableRooms() - count);
        roomTypeRepository.save(roomType);

        log.info("Reserved {} room(s) for roomId {}. Remaining available: {}", count, roomId, roomType.getAvailableRooms());
        return ResponseEntity.ok(Map.of(
                "message", "Rooms reserved successfully",
                "roomType", roomType,
                "remainingAvailable", roomType.getAvailableRooms()
        ));
    }

    /**
     * Release rooms back into inventory (following cancellation or booking expiration).
     * High-privilege inventory operation:
     * - Must NOT be callable by arbitrary unauthenticated/unauthorized users.
     * - Permitted ONLY for:
     *   1) Trusted internal service (booking-service)
     *   2) ROLE_ADMIN
     *   3) The owning ROLE_HOTEL_MANAGER of the property.
     */
    @PutMapping("/rooms/{roomId}/release")
    public ResponseEntity<?> releaseRooms(
            @PathVariable Long roomId,
            @RequestParam Integer count,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        boolean isInternal = securityService.isInternalService(internalService);

        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to release room inventory"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }

            CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
            if (principal == null || !principal.hasManagerOrAdminAccess()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Only trusted internal services, admins, or the owning hotel manager can release inventory"));
            }

            // If hotel manager, enforce room and property ownership
            if (principal.isHotelManager()) {
                RoomType roomType = roomTypeRepository.findById(roomId).orElse(null);
                if (roomType == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Room category not found"));
                }
                Optional<HotelProperty> hotelOpt = hotelRepository.findById(roomType.getHotelId());
                if (hotelOpt.isEmpty() || !principal.userId().equals(hotelOpt.get().getManagerId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Access denied: Cannot release inventory for a property you do not own"));
                }
            }
        }

        if (count == null || count <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Release count must be at least 1"));
        }

        RoomType roomType = roomTypeRepository.findById(roomId).orElse(null);
        if (roomType == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Room category not found"));
        }

        int current = roomType.getAvailableRooms() != null ? roomType.getAvailableRooms() : 0;
        int maxTotal = roomType.getTotalRooms() != null ? roomType.getTotalRooms() : Integer.MAX_VALUE;
        int restored = Math.min(current + count, maxTotal);

        roomType.setAvailableRooms(restored);
        roomTypeRepository.save(roomType);

        log.info("Released {} room(s) for roomId {}. Restored available inventory to: {}", count, roomId, restored);
        return ResponseEntity.ok(Map.of(
                "message", "Rooms released successfully",
                "roomId", roomId,
                "availableRooms", restored
        ));
    }

    /**
     * Soft property deactivation / activation (BD-08).
     * Restricted to the owning ROLE_HOTEL_MANAGER or ROLE_ADMIN.
     */
    @PutMapping("/{hotelId}/status")
    public ResponseEntity<?> updateHotelStatus(
            @PathVariable Long hotelId,
            @RequestParam String status,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to update property status"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        if (principal == null || !principal.hasManagerOrAdminAccess()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Hotel Manager or Admin role required"));
        }

        Optional<HotelProperty> hotelOpt = hotelRepository.findById(hotelId);
        if (hotelOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Hotel property not found"));
        }

        HotelProperty hotel = hotelOpt.get();

        if (principal.isHotelManager() && !principal.userId().equals(hotel.getManagerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Cannot change status for a property you do not own"));
        }

        String normalizedStatus = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid status value. Must be ACTIVE or INACTIVE"));
        }

        hotel.setStatus(normalizedStatus);
        hotelRepository.save(hotel);

        log.info("Hotel property ID {} status updated to '{}' by user ID: {}", hotelId, normalizedStatus, principal.userId());
        return ResponseEntity.ok(Map.of(
                "message", "Hotel status updated successfully",
                "hotelId", hotelId,
                "status", normalizedStatus
        ));
    }
}
