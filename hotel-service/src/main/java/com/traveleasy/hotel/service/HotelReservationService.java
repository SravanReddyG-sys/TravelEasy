package com.traveleasy.hotel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.hotel.dto.ConsolidatedReservationRequestDto;
import com.traveleasy.hotel.dto.HotelReservationResponseDto;
import com.traveleasy.hotel.dto.HotelReservationResponseDto.AllocationDetailDto;
import com.traveleasy.hotel.dto.HotelReservationResponseDto.NightlyPriceDto;
import com.traveleasy.hotel.dto.ReservationAllocationRequestDto;
import com.traveleasy.hotel.exception.DomainValidationException;
import com.traveleasy.hotel.exception.OwnershipAccessDeniedException;
import com.traveleasy.hotel.exception.ResourceNotFoundException;
import com.traveleasy.hotel.model.HotelBookingAllocation;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.HotelReservation;
import com.traveleasy.hotel.model.RoomDailyInventory;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelBookingAllocationRepository;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.HotelReservationRepository;
import com.traveleasy.hotel.repository.RoomDailyInventoryRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import com.traveleasy.hotel.security.HotelSecurityService.CallerPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dedicated aggregate orchestration service for multi-allocation hotel reservations (WP-HB-04).
 * Manages parent HotelReservation aggregate and its child HotelBookingAllocations atomically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotelReservationService {

    private final HotelReservationRepository reservationRepository;
    private final HotelBookingAllocationRepository allocationRepository;
    private final HotelPropertyRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomDailyInventoryRepository dailyInventoryRepository;
    private final HotelPricingService pricingService;
    private final HotelInventoryService inventoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Entry-point for consolidated multi-allocation reservations.
     * Atomically executes reservation within a single database transaction.
     */
    @Transactional
    public HotelReservationResponseDto createConsolidatedReservation(
            ConsolidatedReservationRequestDto dto, boolean isInternal) {

        if (!isInternal) {
            throw new OwnershipAccessDeniedException(
                    "Access denied: Consolidated reservation endpoint is restricted to trusted internal services");
        }

        return executeReservationTransaction(dto);
    }

    /**
     * Executes the atomic multi-allocation reservation within a single database transaction.
     */
    @Transactional
    public HotelReservationResponseDto executeReservationTransaction(ConsolidatedReservationRequestDto dto) {
        // Step 1: Normalize and validate request
        validateBasicRequest(dto);

        // Normalize allocations: group by roomId, aggregate quantities, sort roomId ascending
        List<ReservationAllocationRequestDto> normalizedAllocations = normalizeAllocations(dto.getAllocations());

        // Calculate deterministic SHA-256 request hash
        String requestHash = calculateRequestHash(
                dto.getHotelId(), dto.getCheckInDate(), dto.getCheckOutDate(), normalizedAllocations);

        // Step 2: Handle parent-level idempotency
        Optional<HotelReservation> existingOpt = reservationRepository.findByBookingRef(dto.getBookingRef());
        if (existingOpt.isPresent()) {
            return handleExistingReservationIdempotency(existingOpt.get(), requestHash);
        }

        // Step 3: Validate parent hotel
        HotelProperty hotel = hotelRepository.findById(dto.getHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found with ID: " + dto.getHotelId()));

        if (!"ACTIVE".equalsIgnoreCase(hotel.getStatus())) {
            throw new DomainValidationException("Cannot reserve room inventory for an inactive hotel property");
        }

        // Step 4: Acquire room-type locks in ascending room type ID order
        List<Long> sortedRoomIds = normalizedAllocations.stream()
                .map(ReservationAllocationRequestDto::getRoomId)
                .sorted()
                .toList();

        Map<Long, RoomType> roomTypeMap = new HashMap<>();
        for (Long roomId : sortedRoomIds) {
            RoomType rt = roomTypeRepository.findByIdForUpdate(roomId)
                    .orElseThrow(() -> new ResourceNotFoundException("Room category not found with ID: " + roomId));

            if (!dto.getHotelId().equals(rt.getHotelId())) {
                throw new DomainValidationException(
                        "Room category ID " + roomId + " does not belong to specified hotel ID " + dto.getHotelId());
            }

            if (!"ACTIVE".equalsIgnoreCase(rt.getStatus())) {
                throw new DomainValidationException(
                        "Room category '" + rt.getRoomTypeName() + "' (ID: " + roomId + ") is not active");
            }

            roomTypeMap.put(roomId, rt);
        }

        // Step 5: Ensure daily inventory rows exist for each room type and date
        for (Long roomId : sortedRoomIds) {
            RoomType rt = roomTypeMap.get(roomId);
            ensureDailyInventoryRows(rt, dto.getCheckInDate(), dto.getCheckOutDate());
        }

        // Step 6: Acquire daily inventory locks in deterministic order: Room ID ASC, Date ASC
        Map<Long, List<RoomDailyInventory>> lockedInventoriesMap = new HashMap<>();
        for (Long roomId : sortedRoomIds) {
            List<RoomDailyInventory> lockedRows = dailyInventoryRepository
                    .findByRoomIdAndInventoryDateRangeForUpdate(roomId, dto.getCheckInDate(), dto.getCheckOutDate());

            long expectedNights = ChronoUnit.DAYS.between(dto.getCheckInDate(), dto.getCheckOutDate());
            if (lockedRows.size() != expectedNights) {
                throw new IllegalStateException(
                        "Failed to lock all daily inventory rows for room " + roomId + ". Expected: " + expectedNights + ", Found: " + lockedRows.size());
            }

            lockedInventoriesMap.put(roomId, lockedRows);
        }

        // Step 7: Validate availability and resolve effective nightly pricing across all allocations
        double reservationTotal = 0.0;
        List<AllocationDraft> allocationDrafts = new ArrayList<>();

        for (ReservationAllocationRequestDto allocReq : normalizedAllocations) {
            Long roomId = allocReq.getRoomId();
            int requestedQuantity = allocReq.getQuantity();
            RoomType rt = roomTypeMap.get(roomId);
            List<RoomDailyInventory> lockedRows = lockedInventoriesMap.get(roomId);

            double allocationNightlySum = 0.0;
            List<NightlyPriceDto> nightlyPriceBreakdown = new ArrayList<>();
            List<Map<String, Object>> ratesJsonList = new ArrayList<>();

            for (RoomDailyInventory daily : lockedRows) {
                if (!daily.canAllocate(requestedQuantity)) {
                    throw new DomainValidationException(
                            "Insufficient room inventory for '" + rt.getRoomTypeName() + "' on " +
                                    daily.getInventoryDate() + ". Available: " + daily.getAvailableRooms() +
                                    ", Requested: " + requestedQuantity);
                }

                double nightlyRate = pricingService.resolveEffectivePrice(rt, daily.getInventoryDate());
                boolean isOverride = pricingService.isOverridePresent(roomId, daily.getInventoryDate());

                allocationNightlySum += nightlyRate;

                nightlyPriceBreakdown.add(NightlyPriceDto.builder()
                        .date(daily.getInventoryDate())
                        .rate(nightlyRate)
                        .isOverride(isOverride)
                        .build());

                ratesJsonList.add(Map.of(
                        "date", daily.getInventoryDate().toString(),
                        "rate", nightlyRate,
                        "isOverride", isOverride
                ));
            }

            double allocationTotal = allocationNightlySum * requestedQuantity;
            reservationTotal += allocationTotal;

            String ratesJson = "";
            try {
                ratesJson = objectMapper.writeValueAsString(ratesJsonList);
            } catch (JsonProcessingException ignored) {
            }

            allocationDrafts.add(new AllocationDraft(
                    roomId,
                    rt.getRoomTypeName(),
                    requestedQuantity,
                    allocationTotal,
                    ratesJson,
                    nightlyPriceBreakdown
            ));
        }

        // Step 8 & 9: Update inventory for all allocations (all-or-nothing hold)
        for (ReservationAllocationRequestDto allocReq : normalizedAllocations) {
            Long roomId = allocReq.getRoomId();
            int quantity = allocReq.getQuantity();
            List<RoomDailyInventory> lockedRows = lockedInventoriesMap.get(roomId);

            for (RoomDailyInventory daily : lockedRows) {
                daily.setHeldRooms(daily.getHeldRooms() + quantity);
                dailyInventoryRepository.save(daily);
            }
        }

        // Step 10: Persist parent HotelReservation and child HotelBookingAllocations
        LocalDateTime expiresAt = dto.getExpiresAt() != null
                ? dto.getExpiresAt()
                : LocalDateTime.now().plusMinutes(15);

        HotelReservation reservation = HotelReservation.builder()
                .bookingRef(dto.getBookingRef())
                .hotelId(dto.getHotelId())
                .checkInDate(dto.getCheckInDate())
                .checkOutDate(dto.getCheckOutDate())
                .status("HELD")
                .requestHash(requestHash)
                .totalPrice(reservationTotal)
                .expiresAt(expiresAt)
                .allocations(new ArrayList<>())
                .build();

        for (AllocationDraft draft : allocationDrafts) {
            HotelBookingAllocation allocation = HotelBookingAllocation.builder()
                    .bookingRef(dto.getBookingRef()) // child compatibility field
                    .hotelId(dto.getHotelId())
                    .roomId(draft.roomId())
                    .checkInDate(dto.getCheckInDate())
                    .checkOutDate(dto.getCheckOutDate())
                    .roomCount(draft.quantity())
                    .status("HELD")
                    .totalPrice(draft.allocationTotal())
                    .nightlyRatesJson(draft.ratesJson())
                    .build();

            reservation.addAllocation(allocation);
        }

        HotelReservation savedReservation = reservationRepository.saveAndFlush(reservation);

        // Update legacy availability snapshots
        for (RoomType rt : roomTypeMap.values()) {
            inventoryService.updateLegacyAvailableSnapshot(rt);
        }

        log.info("Successfully created consolidated reservation {} for hotel {} across stay [{} to {}) with {} room allocations. Total: {}",
                savedReservation.getBookingRef(), dto.getHotelId(), dto.getCheckInDate(), dto.getCheckOutDate(),
                savedReservation.getAllocations().size(), savedReservation.getTotalPrice());

        return buildResponseDto(savedReservation, false);
    }

    /**
     * Resolves concurrent reservation attempts for the same bookingRef outside a failing transaction.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public HotelReservationResponseDto handleConcurrentReservationRace(ConsolidatedReservationRequestDto dto) {
        HotelReservation existing = reservationRepository.findByBookingRef(dto.getBookingRef())
                .orElseThrow(() -> new DomainValidationException(
                        "Concurrent reservation conflict: Unable to resolve reservation for bookingRef " + dto.getBookingRef()));

        List<ReservationAllocationRequestDto> normalizedAllocations = normalizeAllocations(dto.getAllocations());
        String currentHash = calculateRequestHash(
                dto.getHotelId(), dto.getCheckInDate(), dto.getCheckOutDate(), normalizedAllocations);

        return handleExistingReservationIdempotency(existing, currentHash);
    }

    /**
     * Confirms all allocations of a parent reservation upon successful payment.
     * Transitions status from HELD -> CONFIRMED and daily inventory from heldRooms -> bookedRooms.
     * Preserves backward compatibility with legacy single-room reservations.
     */
    @Transactional(noRollbackFor = DomainValidationException.class)
    public Map<String, Object> confirmReservation(String bookingRef, CallerPrincipal principal, boolean isInternal) {
        if (bookingRef == null || bookingRef.isBlank()) {
            throw new DomainValidationException("Booking reference is mandatory for confirmation");
        }

        // Check if parent HotelReservation exists
        Optional<HotelReservation> parentOpt = reservationRepository.findByBookingRefForUpdate(bookingRef);

        if (parentOpt.isPresent()) {
            HotelReservation parent = parentOpt.get();

            // Validate authorization
            validateReservationOwnership(parent.getHotelId(), principal, isInternal, "confirm");

            if ("CONFIRMED".equalsIgnoreCase(parent.getStatus())) {
                log.info("Idempotent confirmation request for reservation {}", bookingRef);
                return Map.of(
                        "message", "Reservation already confirmed",
                        "bookingRef", bookingRef,
                        "status", "CONFIRMED",
                        "idempotent", true
                );
            }

            if ("RELEASED".equalsIgnoreCase(parent.getStatus())) {
                throw new DomainValidationException("Cannot confirm released reservation with booking reference: " + bookingRef);
            }

            if ("CANCELLED".equalsIgnoreCase(parent.getStatus())) {
                throw new DomainValidationException("Cannot confirm cancelled reservation with booking reference: " + bookingRef);
            }

            if (!"HELD".equalsIgnoreCase(parent.getStatus())) {
                throw new DomainValidationException("Cannot confirm reservation in invalid status: " + parent.getStatus());
            }

            List<HotelBookingAllocation> allocations = allocationRepository
                    .findByReservationBookingRefOrderByIdAsc(bookingRef);

            if (allocations.isEmpty()) {
                throw new IllegalStateException("Integrity failure: Parent reservation has no child allocations for " + bookingRef);
            }

            // Expiry deadline enforcement (WP-HB-05 Part A)
            if (parent.getExpiresAt() != null && !LocalDateTime.now().isBefore(parent.getExpiresAt())) {
                log.warn("Reservation confirmation rejected in hotel-service: deadline {} has expired for bookingRef {}",
                        parent.getExpiresAt(), bookingRef);
                releaseHeldAllocationsOnExpiry(parent, allocations);
                throw new DomainValidationException("Reservation deadline has expired at " + parent.getExpiresAt() + " and cannot be confirmed: " + bookingRef);
            }

            // Validate parent/child state consistency
            for (HotelBookingAllocation alloc : allocations) {
                if (!"HELD".equalsIgnoreCase(alloc.getStatus())) {
                    throw new DomainValidationException(
                            "Data integrity error: Child allocation ID " + alloc.getId() +
                                    " has status '" + alloc.getStatus() + "' while parent status is 'HELD'");
                }
            }

            // Lock all relevant daily inventory rows in deterministic order: Room ID ASC, Date ASC
            List<Long> roomIds = allocations.stream()
                    .map(HotelBookingAllocation::getRoomId)
                    .distinct()
                    .sorted()
                    .toList();

            Map<Long, Map<LocalDate, RoomDailyInventory>> lockedInventoryMap = new HashMap<>();
            for (Long rId : roomIds) {
                List<RoomDailyInventory> rows = dailyInventoryRepository
                        .findByRoomIdAndInventoryDateRangeForUpdate(rId, parent.getCheckInDate(), parent.getCheckOutDate());
                Map<LocalDate, RoomDailyInventory> dateMap = new HashMap<>();
                for (RoomDailyInventory inv : rows) {
                    dateMap.put(inv.getInventoryDate(), inv);
                }
                lockedInventoryMap.put(rId, dateMap);
            }

            // Pass 1: Strict pre-validation of inventory counters before any mutation
            for (HotelBookingAllocation alloc : allocations) {
                Map<LocalDate, RoomDailyInventory> dateMap = lockedInventoryMap.get(alloc.getRoomId());
                LocalDate cur = alloc.getCheckInDate();
                while (cur.isBefore(alloc.getCheckOutDate())) {
                    RoomDailyInventory daily = (dateMap != null) ? dateMap.get(cur) : null;
                    if (daily == null) {
                        throw new IllegalStateException(
                                "Integrity failure: Missing daily inventory row during confirmation for room " +
                                        alloc.getRoomId() + " on date " + cur);
                    }
                    if (daily.getHeldRooms() < alloc.getRoomCount()) {
                        throw new DomainValidationException(
                                "Integrity failure: Insufficient held rooms (" + daily.getHeldRooms() +
                                        " < " + alloc.getRoomCount() + ") for room " + alloc.getRoomId() + " on date " + cur);
                    }
                    cur = cur.plusDays(1);
                }
            }

            // Pass 2: Mutate inventory counters (transition heldRooms -> bookedRooms)
            for (HotelBookingAllocation alloc : allocations) {
                Map<LocalDate, RoomDailyInventory> dateMap = lockedInventoryMap.get(alloc.getRoomId());
                LocalDate cur = alloc.getCheckInDate();
                while (cur.isBefore(alloc.getCheckOutDate())) {
                    RoomDailyInventory daily = dateMap.get(cur);
                    daily.setHeldRooms(daily.getHeldRooms() - alloc.getRoomCount());
                    daily.setBookedRooms(daily.getBookedRooms() + alloc.getRoomCount());
                    dailyInventoryRepository.save(daily);
                    cur = cur.plusDays(1);
                }

                alloc.setStatus("CONFIRMED");
                alloc.setUpdatedAt(LocalDateTime.now());
                allocationRepository.save(alloc);
            }

            parent.setStatus("CONFIRMED");
            parent.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(parent);

            // Update legacy snapshots
            for (Long rId : roomIds) {
                roomTypeRepository.findById(rId).ifPresent(inventoryService::updateLegacyAvailableSnapshot);
            }

            log.info("Authoritatively confirmed multi-allocation reservation {} ({} allocations) in hotel-service",
                    bookingRef, allocations.size());

            return Map.of(
                    "message", "Stay inventory confirmed successfully",
                    "bookingRef", bookingRef,
                    "status", "CONFIRMED",
                    "totalPrice", parent.getTotalPrice() != null ? parent.getTotalPrice() : 0.0,
                    "allocationsCount", allocations.size()
            );
        }

        // Backward-compatibility: delegate to legacy single-allocation confirmation
        return inventoryService.confirmStayInventory(bookingRef, principal, isInternal);
    }

    /**
     * Releases all allocations of a parent reservation.
     * Decrements heldRooms (if HELD) or bookedRooms (if CONFIRMED) idempotently.
     * Preserves backward compatibility with legacy single-room reservations.
     */
    @Transactional
    public Map<String, Object> releaseReservation(String bookingRef, CallerPrincipal principal, boolean isInternal) {
        if (bookingRef == null || bookingRef.isBlank()) {
            throw new DomainValidationException("Booking reference is mandatory for release");
        }

        Optional<HotelReservation> parentOpt = reservationRepository.findByBookingRefForUpdate(bookingRef);

        if (parentOpt.isPresent()) {
            HotelReservation parent = parentOpt.get();

            // Validate authorization
            validateReservationOwnership(parent.getHotelId(), principal, isInternal, "release");

            if ("RELEASED".equalsIgnoreCase(parent.getStatus())) {
                log.info("Idempotent release request for reservation {}", bookingRef);
                return Map.of(
                        "message", "Inventory already released for booking reference",
                        "bookingRef", bookingRef,
                        "status", "RELEASED",
                        "idempotent", true
                );
            }

            if ("CANCELLED".equalsIgnoreCase(parent.getStatus())) {
                throw new DomainValidationException("Cannot release cancelled reservation with booking reference: " + bookingRef);
            }

            if (!"HELD".equalsIgnoreCase(parent.getStatus()) && !"CONFIRMED".equalsIgnoreCase(parent.getStatus())) {
                throw new DomainValidationException("Cannot release reservation in invalid status: " + parent.getStatus());
            }

            List<HotelBookingAllocation> allocations = allocationRepository
                    .findByReservationBookingRefOrderByIdAsc(bookingRef);

            if (allocations.isEmpty()) {
                throw new IllegalStateException("Integrity failure: Parent reservation has no child allocations for " + bookingRef);
            }

            // Lock all relevant daily inventory rows in deterministic order: Room ID ASC, Date ASC
            List<Long> roomIds = allocations.stream()
                    .map(HotelBookingAllocation::getRoomId)
                    .distinct()
                    .sorted()
                    .toList();

            Map<Long, Map<LocalDate, RoomDailyInventory>> lockedInventoryMap = new HashMap<>();
            for (Long rId : roomIds) {
                List<RoomDailyInventory> rows = dailyInventoryRepository
                        .findByRoomIdAndInventoryDateRangeForUpdate(rId, parent.getCheckInDate(), parent.getCheckOutDate());
                Map<LocalDate, RoomDailyInventory> dateMap = new HashMap<>();
                for (RoomDailyInventory inv : rows) {
                    dateMap.put(inv.getInventoryDate(), inv);
                }
                lockedInventoryMap.put(rId, dateMap);
            }

            // Pass 1: Strict pre-validation of all allocations and inventory counters before mutation
            for (HotelBookingAllocation alloc : allocations) {
                if (!"HELD".equalsIgnoreCase(alloc.getStatus()) && !"CONFIRMED".equalsIgnoreCase(alloc.getStatus()) && !"RELEASED".equalsIgnoreCase(alloc.getStatus())) {
                    throw new DomainValidationException("Integrity failure: Unexpected allocation status '" + alloc.getStatus() + "' for ID " + alloc.getId());
                }

                if ("RELEASED".equalsIgnoreCase(alloc.getStatus())) {
                    continue; // Already released child
                }

                Map<LocalDate, RoomDailyInventory> dateMap = lockedInventoryMap.get(alloc.getRoomId());
                LocalDate cur = alloc.getCheckInDate();
                while (cur.isBefore(alloc.getCheckOutDate())) {
                    RoomDailyInventory daily = (dateMap != null) ? dateMap.get(cur) : null;
                    if (daily == null) {
                        throw new IllegalStateException(
                                "Integrity failure: Missing daily inventory record during release for room " +
                                        alloc.getRoomId() + " on " + cur);
                    }

                    if ("HELD".equalsIgnoreCase(alloc.getStatus())) {
                        if (daily.getHeldRooms() < alloc.getRoomCount()) {
                            throw new DomainValidationException(
                                    "Integrity failure: Insufficient held rooms (" + daily.getHeldRooms() +
                                            " < " + alloc.getRoomCount() + ") for room " + alloc.getRoomId() + " on " + cur);
                        }
                    } else if ("CONFIRMED".equalsIgnoreCase(alloc.getStatus())) {
                        if (daily.getBookedRooms() < alloc.getRoomCount()) {
                            throw new DomainValidationException(
                                    "Integrity failure: Insufficient booked rooms (" + daily.getBookedRooms() +
                                            " < " + alloc.getRoomCount() + ") for room " + alloc.getRoomId() + " on " + cur);
                        }
                    }

                    cur = cur.plusDays(1);
                }
            }

            // Pass 2: Mutate daily inventory counters and persist release
            for (HotelBookingAllocation alloc : allocations) {
                if ("RELEASED".equalsIgnoreCase(alloc.getStatus())) {
                    continue; // Already released child
                }

                Map<LocalDate, RoomDailyInventory> dateMap = lockedInventoryMap.get(alloc.getRoomId());
                LocalDate cur = alloc.getCheckInDate();
                while (cur.isBefore(alloc.getCheckOutDate())) {
                    RoomDailyInventory daily = dateMap.get(cur);

                    if ("HELD".equalsIgnoreCase(alloc.getStatus())) {
                        daily.setHeldRooms(daily.getHeldRooms() - alloc.getRoomCount());
                    } else if ("CONFIRMED".equalsIgnoreCase(alloc.getStatus())) {
                        daily.setBookedRooms(daily.getBookedRooms() - alloc.getRoomCount());
                    }
                    dailyInventoryRepository.save(daily);

                    cur = cur.plusDays(1);
                }

                alloc.setStatus("RELEASED");
                alloc.setUpdatedAt(LocalDateTime.now());
                allocationRepository.save(alloc);
            }

            parent.setStatus("RELEASED");
            parent.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(parent);

            // Update legacy snapshots
            for (Long rId : roomIds) {
                roomTypeRepository.findById(rId).ifPresent(inventoryService::updateLegacyAvailableSnapshot);
            }

            log.info("Authoritatively released multi-allocation reservation {} ({} allocations) in hotel-service",
                    bookingRef, allocations.size());

            return Map.of(
                    "message", "Stay inventory released successfully",
                    "bookingRef", bookingRef,
                    "status", "RELEASED",
                    "allocationsCount", allocations.size()
            );
        }

        // Backward-compatibility: delegate to legacy single-allocation release
        return inventoryService.releaseStayInventory(bookingRef, principal, isInternal);
    }

    /**
     * Retrieves full consolidated reservation details by booking reference.
     */
    @Transactional(readOnly = true)
    public HotelReservationResponseDto getReservation(String bookingRef) {
        HotelReservation res = reservationRepository.findByBookingRefWithAllocations(bookingRef)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with bookingRef: " + bookingRef));
        return buildResponseDto(res, false);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void validateBasicRequest(ConsolidatedReservationRequestDto dto) {
        if (dto.getBookingRef() == null || dto.getBookingRef().isBlank()) {
            throw new DomainValidationException("Booking reference is mandatory");
        }
        if (dto.getHotelId() == null) {
            throw new DomainValidationException("Hotel ID is mandatory");
        }
        if (dto.getCheckInDate() == null) {
            throw new DomainValidationException("Check-in date is mandatory");
        }
        if (dto.getCheckOutDate() == null) {
            throw new DomainValidationException("Check-out date is mandatory");
        }
        if (!dto.getCheckOutDate().isAfter(dto.getCheckInDate())) {
            throw new DomainValidationException("Check-out date must be strictly after check-in date");
        }
        if (dto.getCheckInDate().isBefore(LocalDate.now())) {
            throw new DomainValidationException("Cannot reserve room inventory for past dates");
        }
        if (dto.getAllocations() == null || dto.getAllocations().isEmpty()) {
            throw new DomainValidationException("At least one room allocation is required");
        }
        for (ReservationAllocationRequestDto alloc : dto.getAllocations()) {
            if (alloc.getRoomId() == null) {
                throw new DomainValidationException("Room ID is mandatory for all allocations");
            }
            if (alloc.getQuantity() == null || alloc.getQuantity() <= 0) {
                throw new DomainValidationException("Allocation quantity must be greater than zero for room ID " + alloc.getRoomId());
            }
        }
    }

    private List<ReservationAllocationRequestDto> normalizeAllocations(List<ReservationAllocationRequestDto> rawAllocations) {
        Map<Long, Integer> aggregated = new TreeMap<>(); // sorted by roomId ascending
        for (ReservationAllocationRequestDto alloc : rawAllocations) {
            aggregated.merge(alloc.getRoomId(), alloc.getQuantity(), Integer::sum);
        }

        return aggregated.entrySet().stream()
                .map(e -> new ReservationAllocationRequestDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public String calculateRequestHash(
            Long hotelId, LocalDate checkIn, LocalDate checkOut, List<ReservationAllocationRequestDto> sortedAllocations) {
        StringBuilder sb = new StringBuilder();
        sb.append(hotelId).append(":").append(checkIn).append(":").append(checkOut).append(":");
        for (int i = 0; i < sortedAllocations.size(); i++) {
            if (i > 0) sb.append(",");
            ReservationAllocationRequestDto a = sortedAllocations.get(i);
            sb.append(a.getRoomId()).append("x").append(a.getQuantity());
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private HotelReservationResponseDto handleExistingReservationIdempotency(
            HotelReservation existing, String requestHash) {
        if (!Objects.equals(existing.getRequestHash(), requestHash)) {
            throw new DomainValidationException(
                    "Booking reference '" + existing.getBookingRef() +
                            "' is already in use with different reservation parameters");
        }

        if ("HELD".equalsIgnoreCase(existing.getStatus()) || "CONFIRMED".equalsIgnoreCase(existing.getStatus())) {
            log.info("Idempotent consolidated reservation request for bookingRef {}: returning existing {} reservation",
                    existing.getBookingRef(), existing.getStatus());
            return buildResponseDto(existing, true);
        } else if ("RELEASED".equalsIgnoreCase(existing.getStatus())) {
            throw new DomainValidationException(
                    "Booking reference " + existing.getBookingRef() + " was previously released and cannot be re-reserved");
        } else if ("CANCELLED".equalsIgnoreCase(existing.getStatus())) {
            throw new DomainValidationException(
                    "Booking reference " + existing.getBookingRef() + " was previously cancelled and cannot be re-reserved");
        } else {
            throw new DomainValidationException(
                    "Reservation " + existing.getBookingRef() + " is in unexpected state: " + existing.getStatus());
        }
    }

    private void ensureDailyInventoryRows(RoomType roomType, LocalDate checkIn, LocalDate checkOut) {
        LocalDate cur = checkIn;
        int capacity = roomType.getTotalRooms() != null ? roomType.getTotalRooms() : 0;
        while (cur.isBefore(checkOut)) {
            dailyInventoryRepository.insertIfNotExists(
                    roomType.getHotelId(), roomType.getId(), cur, capacity);
            cur = cur.plusDays(1);
        }
    }

    private void validateReservationOwnership(
            Long hotelId, CallerPrincipal principal, boolean isInternal, String operation) {
        if (isInternal) {
            return;
        }

        if (principal == null) {
            throw new OwnershipAccessDeniedException("Authentication required to " + operation + " reservation");
        }

        if (principal.isAdmin()) {
            return;
        }

        if (principal.isHotelManager()) {
            HotelProperty hotel = hotelRepository.findById(hotelId)
                    .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found with ID: " + hotelId));

            if (!principal.userId().equals(hotel.getManagerId())) {
                throw new OwnershipAccessDeniedException(
                        "Access denied: Cannot " + operation + " reservation for a property you do not own");
            }
            return;
        }

        throw new OwnershipAccessDeniedException(
                "Access denied: Only trusted internal services, admins, or the owning hotel manager can " + operation + " reservations");
    }

    private HotelReservationResponseDto buildResponseDto(HotelReservation res, boolean idempotent) {
        int nights = (int) ChronoUnit.DAYS.between(res.getCheckInDate(), res.getCheckOutDate());

        List<AllocationDetailDto> allocationDtos = new ArrayList<>();
        List<HotelBookingAllocation> allocList = res.getAllocations();
        if (allocList == null || allocList.isEmpty()) {
            allocList = allocationRepository.findByReservationBookingRefOrderByIdAsc(res.getBookingRef());
        }

        for (HotelBookingAllocation alloc : allocList) {
            String roomTypeName = roomTypeRepository.findById(alloc.getRoomId())
                    .map(RoomType::getRoomTypeName)
                    .orElse("Room #" + alloc.getRoomId());

            List<NightlyPriceDto> breakdown = new ArrayList<>();
            if (alloc.getNightlyRatesJson() != null && !alloc.getNightlyRatesJson().isBlank()) {
                try {
                    List<Map<String, Object>> parsed = objectMapper.readValue(
                            alloc.getNightlyRatesJson(), new TypeReference<>() {});
                    for (Map<String, Object> entry : parsed) {
                        LocalDate d = LocalDate.parse((String) entry.get("date"));
                        Double rate = ((Number) entry.get("rate")).doubleValue();
                        Boolean override = (Boolean) entry.get("isOverride");
                        breakdown.add(new NightlyPriceDto(d, rate, override));
                    }
                } catch (Exception ignored) {
                }
            }

            allocationDtos.add(AllocationDetailDto.builder()
                    .allocationId(alloc.getId())
                    .roomId(alloc.getRoomId())
                    .roomTypeName(roomTypeName)
                    .quantity(alloc.getRoomCount())
                    .allocationTotal(alloc.getTotalPrice())
                    .nightlyPriceBreakdown(breakdown)
                    .build());
        }

        return HotelReservationResponseDto.builder()
                .bookingRef(res.getBookingRef())
                .hotelId(res.getHotelId())
                .checkInDate(res.getCheckInDate())
                .checkOutDate(res.getCheckOutDate())
                .totalNights(nights)
                .status(res.getStatus())
                .totalPrice(res.getTotalPrice())
                .expiresAt(res.getExpiresAt())
                .idempotent(idempotent)
                .allocations(allocationDtos)
                .build();
    }

    private void releaseHeldAllocationsOnExpiry(HotelReservation parent, List<HotelBookingAllocation> allocations) {
        List<Long> roomIds = allocations.stream()
                .map(HotelBookingAllocation::getRoomId)
                .distinct()
                .sorted()
                .toList();

        Map<Long, Map<LocalDate, RoomDailyInventory>> lockedInventoryMap = new HashMap<>();
        for (Long rId : roomIds) {
            List<RoomDailyInventory> rows = dailyInventoryRepository
                    .findByRoomIdAndInventoryDateRangeForUpdate(rId, parent.getCheckInDate(), parent.getCheckOutDate());
            Map<LocalDate, RoomDailyInventory> dateMap = new HashMap<>();
            for (RoomDailyInventory inv : rows) {
                dateMap.put(inv.getInventoryDate(), inv);
            }
            lockedInventoryMap.put(rId, dateMap);
        }

        for (HotelBookingAllocation alloc : allocations) {
            if ("RELEASED".equalsIgnoreCase(alloc.getStatus())) {
                continue;
            }
            Map<LocalDate, RoomDailyInventory> dateMap = lockedInventoryMap.get(alloc.getRoomId());
            LocalDate cur = alloc.getCheckInDate();
            while (cur.isBefore(alloc.getCheckOutDate())) {
                RoomDailyInventory daily = dateMap != null ? dateMap.get(cur) : null;
                if (daily != null) {
                    if ("HELD".equalsIgnoreCase(alloc.getStatus())) {
                        daily.setHeldRooms(Math.max(0, daily.getHeldRooms() - alloc.getRoomCount()));
                        dailyInventoryRepository.save(daily);
                    }
                }
                cur = cur.plusDays(1);
            }
            alloc.setStatus("RELEASED");
            alloc.setUpdatedAt(LocalDateTime.now());
            allocationRepository.save(alloc);
        }

        parent.setStatus("RELEASED");
        parent.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(parent);

        for (Long rId : roomIds) {
            roomTypeRepository.findById(rId).ifPresent(inventoryService::updateLegacyAvailableSnapshot);
        }
        log.info("Auto-released expired reservation {} ({} allocations) upon late confirmation attempt",
                parent.getBookingRef(), allocations.size());
    }

    private record AllocationDraft(
            Long roomId,
            String roomTypeName,
            int quantity,
            double allocationTotal,
            String ratesJson,
            List<NightlyPriceDto> nightlyPriceBreakdown
    ) {
    }
}
