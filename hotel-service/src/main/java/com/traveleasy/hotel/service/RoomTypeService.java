package com.traveleasy.hotel.service;

import com.traveleasy.hotel.dto.RoomTypeRequestDto;
import com.traveleasy.hotel.exception.DomainValidationException;
import com.traveleasy.hotel.exception.OwnershipAccessDeniedException;
import com.traveleasy.hotel.exception.ResourceNotFoundException;
import com.traveleasy.hotel.model.HotelBookingAllocation;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomDailyInventory;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelBookingAllocationRepository;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.RoomDailyInventoryRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import com.traveleasy.hotel.security.HotelSecurityService.CallerPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final HotelPropertyRepository hotelRepository;
    private final HotelBookingAllocationRepository allocationRepository;
    private final RoomDailyInventoryRepository dailyInventoryRepository;
    @Lazy
    private final HotelInventoryService inventoryService;

    @Transactional
    public RoomType createRoomType(Long hotelId, RoomTypeRequestDto request, CallerPrincipal principal) {
        validatePrincipal(principal);
        validateRoomRequest(request);

        HotelProperty hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found with ID: " + hotelId));

        enforcePropertyOwnership(hotel, principal);

        // Check for duplicate active room category name within this hotel
        Optional<RoomType> existing = roomTypeRepository.findByHotelIdAndRoomTypeNameIgnoreCaseAndStatus(
                hotelId, request.getRoomTypeName().trim(), "ACTIVE");
        if (existing.isPresent()) {
            throw new DomainValidationException("Room category '" + request.getRoomTypeName().trim() + "' already exists for this property");
        }

        RoomType roomType = RoomType.builder()
                .hotelId(hotelId)
                .roomTypeName(request.getRoomTypeName().trim())
                .maxOccupancy(request.getMaxOccupancy() != null ? request.getMaxOccupancy() : 2)
                .pricePerNight(request.getPricePerNight())
                .totalRooms(request.getTotalRooms())
                .availableRooms(request.getTotalRooms())
                .amenities(request.getAmenities() != null ? request.getAmenities().trim() : null)
                .imageUrl(request.getImageUrl() != null && !request.getImageUrl().trim().isEmpty() ? request.getImageUrl().trim() : null)
                .status("ACTIVE")
                .build();

        RoomType saved = roomTypeRepository.save(roomType);
        
        // BD-06: Automatically initialize today's daily inventory record
        if (inventoryService != null) {
            inventoryService.getOrCreateDailyInventory(saved, LocalDate.now());
        }

        log.info("Room category '{}' (ID: {}) created for hotel ID: {} by user ID: {}",
                saved.getRoomTypeName(), saved.getId(), hotelId, principal.userId());
        return saved;
    }

    @Transactional
    public RoomType updateRoomType(Long roomId, RoomTypeRequestDto request, CallerPrincipal principal) {
        validatePrincipal(principal);
        validateRoomRequest(request);

        RoomType roomType = roomTypeRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room category not found with ID: " + roomId));

        HotelProperty hotel = hotelRepository.findById(roomType.getHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent hotel property not found with ID: " + roomType.getHotelId()));

        enforcePropertyOwnership(hotel, principal);

        if ("ARCHIVED".equalsIgnoreCase(roomType.getStatus())) {
            throw new DomainValidationException("Cannot modify an archived room category");
        }

        // Duplicate room name check if renamed
        Optional<RoomType> conflict = roomTypeRepository.findByHotelIdAndRoomTypeNameIgnoreCaseAndStatus(
                roomType.getHotelId(), request.getRoomTypeName().trim(), "ACTIVE");
        if (conflict.isPresent() && !conflict.get().getId().equals(roomId)) {
            throw new DomainValidationException("Another active room category with name '" + request.getRoomTypeName().trim() + "' already exists in this property");
        }

        int oldTotal = roomType.getTotalRooms() != null ? roomType.getTotalRooms() : 0;
        int oldAvailable = roomType.getAvailableRooms() != null ? roomType.getAvailableRooms() : 0;
        int allocatedRooms = Math.max(0, oldTotal - oldAvailable);
        int newTotal = request.getTotalRooms();

        // BD-07: Inventory reduction must not violate existing allocations
        if (newTotal < allocatedRooms) {
            throw new DomainValidationException("Cannot reduce total inventory to " + newTotal +
                    " room(s) because " + allocatedRooms + " room(s) are currently reserved or allocated.");
        }

        // BD-07 / WP-03: Validate and apply capacity change across date-wise future allocations
        if (inventoryService != null) {
            inventoryService.validateAndApplyCapacityChange(roomType, newTotal);
        }

        int delta = newTotal - oldTotal;
        int newAvailable = Math.max(0, oldAvailable + delta);
        roomType.setTotalRooms(newTotal);
        roomType.setAvailableRooms(newAvailable);

        // Determine whether active bookings or allocations exist for this room category
        boolean hasActiveBookings = allocatedRooms > 0;
        if (!hasActiveBookings && allocationRepository != null) {
            List<HotelBookingAllocation> activeAllocs = allocationRepository.findByRoomIdAndStatusIn(
                    roomId, List.of("HELD", "CONFIRMED"));
            LocalDate today = LocalDate.now();
            hasActiveBookings = activeAllocs.stream()
                    .anyMatch(a -> a.getCheckOutDate() == null || !a.getCheckOutDate().isBefore(today));
        }
        if (!hasActiveBookings && dailyInventoryRepository != null) {
            List<RoomDailyInventory> futureInvs = dailyInventoryRepository
                    .findByRoomIdAndInventoryDateGreaterThanEqualOrderByInventoryDateAsc(roomId, LocalDate.now());
            hasActiveBookings = futureInvs.stream()
                    .anyMatch(inv -> (inv.getBookedRooms() != null && inv.getBookedRooms() > 0)
                            || (inv.getHeldRooms() != null && inv.getHeldRooms() > 0));
        }

        // BD-07: Structural modification check for room occupancy (bidirectional)
        int currentOccupancy = roomType.getMaxOccupancy() != null ? roomType.getMaxOccupancy() : 2;
        int targetOccupancy = request.getMaxOccupancy() != null ? request.getMaxOccupancy() : currentOccupancy;
        if (targetOccupancy != currentOccupancy && hasActiveBookings) {
            if (targetOccupancy < currentOccupancy) {
                throw new DomainValidationException("Cannot reduce maximum occupancy from " +
                        currentOccupancy + " to " + targetOccupancy +
                        " while active bookings exist for this room type. Maximum occupancy is structural and cannot be modified when reservations exist.");
            } else {
                throw new DomainValidationException("Cannot increase maximum occupancy from " +
                        currentOccupancy + " to " + targetOccupancy +
                        " while active bookings exist for this room type. Maximum occupancy is structural and cannot be modified when reservations exist.");
            }
        }
        roomType.setMaxOccupancy(targetOccupancy);

        // Non-structural updates (amenities, name, base price, imageUrl)
        roomType.setRoomTypeName(request.getRoomTypeName().trim());
        roomType.setAmenities(request.getAmenities() != null ? request.getAmenities().trim() : null);
        if (request.getPricePerNight() != null) {
            roomType.setPricePerNight(request.getPricePerNight());
        }
        if (request.getImageUrl() != null) {
            roomType.setImageUrl(request.getImageUrl().trim().isEmpty() ? null : request.getImageUrl().trim());
        }

        RoomType updated = roomTypeRepository.save(roomType);
        log.info("Room category ID {} updated by user ID: {}", roomId, principal.userId());
        return updated;
    }

    @Transactional
    public RoomType archiveRoomType(Long roomId, CallerPrincipal principal) {
        validatePrincipal(principal);

        RoomType roomType = roomTypeRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room category not found with ID: " + roomId));

        HotelProperty hotel = hotelRepository.findById(roomType.getHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent hotel property not found with ID: " + roomType.getHotelId()));

        enforcePropertyOwnership(hotel, principal);

        int oldTotal = roomType.getTotalRooms() != null ? roomType.getTotalRooms() : 0;
        int oldAvailable = roomType.getAvailableRooms() != null ? roomType.getAvailableRooms() : 0;
        int allocatedRooms = Math.max(0, oldTotal - oldAvailable);

        // Cannot delete/archive if active allocations exist
        boolean hasActiveBookings = allocatedRooms > 0;
        if (!hasActiveBookings && allocationRepository != null) {
            List<HotelBookingAllocation> activeAllocs = allocationRepository.findByRoomIdAndStatusIn(
                    roomId, List.of("HELD", "CONFIRMED"));
            LocalDate today = LocalDate.now();
            hasActiveBookings = activeAllocs.stream()
                    .anyMatch(a -> a.getCheckOutDate() == null || !a.getCheckOutDate().isBefore(today));
        }
        if (!hasActiveBookings && dailyInventoryRepository != null) {
            List<RoomDailyInventory> futureInvs = dailyInventoryRepository
                    .findByRoomIdAndInventoryDateGreaterThanEqualOrderByInventoryDateAsc(roomId, LocalDate.now());
            hasActiveBookings = futureInvs.stream()
                    .anyMatch(inv -> (inv.getBookedRooms() != null && inv.getBookedRooms() > 0)
                            || (inv.getHeldRooms() != null && inv.getHeldRooms() > 0));
        }
        if (hasActiveBookings) {
            throw new DomainValidationException("Cannot archive room category while active reservations exist.");
        }

        // BD-07: Historical room types must not be permanently deleted
        roomType.setStatus("ARCHIVED");
        roomType.setAvailableRooms(0);

        RoomType archived = roomTypeRepository.save(roomType);
        log.info("Room category ID {} soft-archived by user ID: {}", roomId, principal.userId());
        return archived;
    }

    @Transactional
    public RoomType restoreRoomType(Long roomId, CallerPrincipal principal) {
        validatePrincipal(principal);

        RoomType roomType = roomTypeRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room category not found with ID: " + roomId));

        HotelProperty hotel = hotelRepository.findById(roomType.getHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent hotel property not found with ID: " + roomType.getHotelId()));

        enforcePropertyOwnership(hotel, principal);

        if (!"ARCHIVED".equalsIgnoreCase(roomType.getStatus())) {
            throw new DomainValidationException("Room category is not archived (current status: " + roomType.getStatus() + ")");
        }

        // Check if an active room category with the same name already exists in this property
        Optional<RoomType> existingActive = roomTypeRepository.findByHotelIdAndRoomTypeNameIgnoreCaseAndStatus(
                roomType.getHotelId(), roomType.getRoomTypeName().trim(), "ACTIVE");
        if (existingActive.isPresent() && !existingActive.get().getId().equals(roomId)) {
            throw new DomainValidationException("Cannot restore room category: an active room category with name '"
                    + roomType.getRoomTypeName().trim() + "' already exists for this property");
        }

        roomType.setStatus("ACTIVE");
        roomType.setAvailableRooms(roomType.getTotalRooms() != null ? roomType.getTotalRooms() : 0);

        RoomType restored = roomTypeRepository.save(roomType);
        log.info("Room category ID {} restored to ACTIVE by user ID: {}", roomId, principal.userId());
        return restored;
    }

    @Transactional(readOnly = true)
    public List<RoomType> getRoomTypesByHotel(Long hotelId, CallerPrincipal principal) {
        boolean isOwnerOrAdmin = false;
        if (principal != null && principal.hasManagerOrAdminAccess()) {
            Optional<HotelProperty> hotelOpt = hotelRepository.findById(hotelId);
            if (hotelOpt.isPresent()) {
                isOwnerOrAdmin = principal.isAdmin() || principal.userId().equals(hotelOpt.get().getManagerId());
            }
        }

        if (isOwnerOrAdmin) {
            return roomTypeRepository.findByHotelId(hotelId);
        } else {
            return roomTypeRepository.findByHotelIdAndStatus(hotelId, "ACTIVE");
        }
    }

    @Transactional(readOnly = true)
    public RoomType getRoomTypeById(Long roomId) {
        return roomTypeRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room category not found with ID: " + roomId));
    }

    private void validatePrincipal(CallerPrincipal principal) {
        if (principal == null || !principal.hasManagerOrAdminAccess()) {
            throw new OwnershipAccessDeniedException("Access denied: Hotel Manager or Admin role required");
        }
        if (principal.userId() == null || principal.userId() <= 0) {
            throw new OwnershipAccessDeniedException("Access denied: Missing or invalid user identity in token");
        }
    }

    private void enforcePropertyOwnership(HotelProperty hotel, CallerPrincipal principal) {
        if (principal.isHotelManager() && !principal.userId().equals(hotel.getManagerId())) {
            throw new OwnershipAccessDeniedException("Access denied: Cannot add room categories to a property you do not own");
        }
    }

    private void validateRoomRequest(RoomTypeRequestDto request) {
        if (request == null) {
            throw new DomainValidationException("Request body cannot be null");
        }
        if (request.getRoomTypeName() == null || request.getRoomTypeName().trim().isEmpty()) {
            throw new DomainValidationException("Room category name is mandatory");
        }
        if (request.getMaxOccupancy() != null && request.getMaxOccupancy() < 1) {
            throw new DomainValidationException("Room max occupancy must be at least 1 person");
        }
        if (request.getMaxOccupancy() != null && request.getMaxOccupancy() > 20) {
            throw new DomainValidationException("Room max occupancy cannot exceed 20 people");
        }
        if (request.getPricePerNight() != null && request.getPricePerNight() <= 0.0) {
            throw new DomainValidationException("Price per night must be greater than zero");
        }
        if (request.getTotalRooms() == null || request.getTotalRooms() < 1) {
            throw new DomainValidationException("Total rooms inventory must be at least 1");
        }
    }
}
