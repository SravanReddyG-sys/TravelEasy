package com.traveleasy.hotel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.hotel.dto.DailyInventoryDto;
import com.traveleasy.hotel.dto.HotelInventorySummaryDto;
import com.traveleasy.hotel.dto.DateWiseReserveRequestDto;
import com.traveleasy.hotel.dto.StayAvailabilityResponseDto;
import com.traveleasy.hotel.dto.StayAvailabilityResponseDto.NightlyRateDto;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelInventoryService {

    private final RoomTypeRepository roomTypeRepository;
    private final HotelPropertyRepository hotelRepository;
    private final RoomDailyInventoryRepository dailyInventoryRepository;
    private final HotelBookingAllocationRepository allocationRepository;
    private final HotelPricingService pricingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /**
     * Retrieves or automatically initializes a RoomDailyInventory record (BD-06).
     */
    @Transactional
    public RoomDailyInventory getOrCreateDailyInventory(RoomType roomType, LocalDate date) {
        Optional<RoomDailyInventory> existing = dailyInventoryRepository
                .findByRoomIdAndInventoryDate(roomType.getId(), date);

        if (existing.isPresent()) {
            RoomDailyInventory inv = existing.get();
            if (roomType.getTotalRooms() != null && !roomType.getTotalRooms().equals(inv.getTotalRooms())) {
                if (roomType.getTotalRooms() >= inv.getTotalAllocated()) {
                    inv.setTotalRooms(roomType.getTotalRooms());
                    return dailyInventoryRepository.save(inv);
                }
            }
            return inv;
        }

        int capacity = roomType.getTotalRooms() != null ? roomType.getTotalRooms() : 0;
        RoomDailyInventory newInv = RoomDailyInventory.builder()
                .hotelId(roomType.getHotelId())
                .roomId(roomType.getId())
                .inventoryDate(date)
                .totalRooms(capacity)
                .bookedRooms(0)
                .heldRooms(0)
                .blockedRooms(0)
                .build();

        try {
            return dailyInventoryRepository.saveAndFlush(newInv);
        } catch (DataIntegrityViolationException ex) {
            return dailyInventoryRepository.findByRoomIdAndInventoryDate(roomType.getId(), date)
                    .orElseThrow(() -> new IllegalStateException("Failed to resolve daily inventory record for date: " + date));
        }
    }

    /**
     * Checks multi-night availability and computes exact pricing for a requested stay.
     * Check-in date is inclusive; check-out date is exclusive (BD-01).
     */
    @Transactional(readOnly = true)
    public StayAvailabilityResponseDto checkStayAvailability(
            Long roomId, LocalDate checkInDate, LocalDate checkOutDate, Integer requestedRooms) {

        RoomType roomType = roomTypeRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room category not found with ID: " + roomId));

        int roomsNeeded = (requestedRooms == null || requestedRooms <= 0) ? 1 : requestedRooms;

        if (checkInDate == null) {
            checkInDate = LocalDate.now();
        }
        if (checkOutDate == null) {
            checkOutDate = checkInDate.plusDays(1);
        }

        if (!checkOutDate.isAfter(checkInDate)) {
            throw new DomainValidationException("Check-out date must be after check-in date");
        }

        int totalNights = 0;
        int minAvailableAcrossStay = Integer.MAX_VALUE;
        double totalPriceSum = 0.0;
        double minNightly = Double.MAX_VALUE;
        double maxNightly = 0.0;
        List<NightlyRateDto> breakdown = new ArrayList<>();

        LocalDate cur = checkInDate;
        while (cur.isBefore(checkOutDate)) {
            totalNights++;
            final LocalDate lookupDate = cur;
            RoomDailyInventory daily = dailyInventoryRepository
                    .findByRoomIdAndInventoryDate(roomId, lookupDate)
                    .orElseGet(() -> RoomDailyInventory.builder()
                            .hotelId(roomType.getHotelId())
                            .roomId(roomId)
                            .inventoryDate(lookupDate)
                            .totalRooms(roomType.getTotalRooms() != null ? roomType.getTotalRooms() : 0)
                            .bookedRooms(0)
                            .heldRooms(0)
                            .blockedRooms(0)
                            .build());

            int available = daily.getAvailableRooms();
            if (available < minAvailableAcrossStay) {
                minAvailableAcrossStay = available;
            }

            double nightlyRate = pricingService.resolveEffectivePrice(roomType, lookupDate);
            boolean isOverride = pricingService.isOverridePresent(roomId, lookupDate);

            if (nightlyRate < minNightly) minNightly = nightlyRate;
            if (nightlyRate > maxNightly) maxNightly = nightlyRate;
            totalPriceSum += nightlyRate;

            breakdown.add(NightlyRateDto.builder()
                    .date(lookupDate)
                    .price(nightlyRate)
                    .isOverride(isOverride)
                    .availableRooms(available)
                    .build());

            cur = cur.plusDays(1);
        }

        if (minAvailableAcrossStay == Integer.MAX_VALUE) {
            minAvailableAcrossStay = 0;
        }
        if (minNightly == Double.MAX_VALUE) {
            minNightly = 0.0;
        }

        boolean isAvailable = "ACTIVE".equalsIgnoreCase(roomType.getStatus())
                && minAvailableAcrossStay >= roomsNeeded;
        double totalPrice = totalPriceSum * roomsNeeded;

        return StayAvailabilityResponseDto.builder()
                .hotelId(roomType.getHotelId())
                .roomId(roomId)
                .roomTypeName(roomType.getRoomTypeName())
                .checkInDate(checkInDate)
                .checkOutDate(checkOutDate)
                .requestedRooms(roomsNeeded)
                .available(isAvailable)
                .availableRoomsAcrossStay(minAvailableAcrossStay)
                .totalNights(totalNights)
                .minNightlyPrice(minNightly)
                .maxNightlyPrice(maxNightly)
                .totalPrice(totalPrice)
                .nightlyBreakdown(breakdown)
                .build();
    }

    /**
     * Atomically reserves date-wise room inventory across all nights of a stay.
     * Uses pessimistic locking on daily inventory records to prevent overselling.
     */
    @Transactional
    public Map<String, Object> reserveStayInventory(
            Long roomId, DateWiseReserveRequestDto dto, CallerPrincipal principal, boolean isInternal) {

        if (dto.getBookingRef() == null || dto.getBookingRef().isBlank()) {
            throw new DomainValidationException("Booking reference is mandatory for reservation");
        }

        // Idempotency check: if an allocation already exists for this bookingRef, return it
        Optional<HotelBookingAllocation> existingAlloc = allocationRepository.findByBookingRef(dto.getBookingRef());
        if (existingAlloc.isPresent()) {
            HotelBookingAllocation alloc = existingAlloc.get();
            if ("HELD".equals(alloc.getStatus()) || "CONFIRMED".equals(alloc.getStatus())) {
                log.info("Idempotent reservation request for bookingRef {}: returning existing {} allocation",
                        dto.getBookingRef(), alloc.getStatus());
                return Map.of(
                        "message", "Reservation already active for this booking reference",
                        "bookingRef", alloc.getBookingRef(),
                        "status", alloc.getStatus(),
                        "roomId", alloc.getRoomId(),
                        "totalPrice", alloc.getTotalPrice() != null ? alloc.getTotalPrice() : 0.0,
                        "roomCount", alloc.getRoomCount(),
                        "idempotent", true
                );
            } else if ("RELEASED".equals(alloc.getStatus())) {
                throw new DomainValidationException("Booking reference " + dto.getBookingRef() + " was previously released and cannot be re-reserved");
            }
        }

        LocalDate checkIn = dto.getCheckInDate() != null ? dto.getCheckInDate() : LocalDate.now();
        LocalDate checkOut = dto.getCheckOutDate() != null ? dto.getCheckOutDate() : checkIn.plusDays(1);
        int count = (dto.getCount() != null && dto.getCount() > 0) ? dto.getCount() : 1;

        if (!checkOut.isAfter(checkIn)) {
            throw new DomainValidationException("Check-out date must be strictly after check-in date");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new DomainValidationException("Cannot reserve room inventory for past dates");
        }

        RoomType roomType = roomTypeRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room category not found with ID: " + roomId));

        if (!"ACTIVE".equalsIgnoreCase(roomType.getStatus())) {
            throw new DomainValidationException("Room category is not active and cannot accept new reservations");
        }

        HotelProperty hotel = hotelRepository.findById(roomType.getHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found with ID: " + roomType.getHotelId()));
        if (!"ACTIVE".equalsIgnoreCase(hotel.getStatus())) {
            throw new DomainValidationException("Cannot reserve room inventory for an inactive hotel property");
        }

        // Step 1: Pessimistic locking & validation across all occupied nights
        List<RoomDailyInventory> lockedInventories = new ArrayList<>();
        double totalNightlySum = 0.0;
        List<Map<String, Object>> ratesBreakdown = new ArrayList<>();

        LocalDate cur = checkIn;
        while (cur.isBefore(checkOut)) {
            final LocalDate dateForLock = cur;
            RoomDailyInventory daily = dailyInventoryRepository
                    .findByRoomIdAndInventoryDateForUpdate(roomId, dateForLock)
                    .orElseGet(() -> {
                        RoomDailyInventory newInv = RoomDailyInventory.builder()
                                .hotelId(roomType.getHotelId())
                                .roomId(roomId)
                                .inventoryDate(dateForLock)
                                .totalRooms(roomType.getTotalRooms() != null ? roomType.getTotalRooms() : 0)
                                .bookedRooms(0)
                                .heldRooms(0)
                                .blockedRooms(0)
                                .build();
                        try {
                            return dailyInventoryRepository.saveAndFlush(newInv);
                        } catch (Exception e) {
                            return dailyInventoryRepository.findByRoomIdAndInventoryDateForUpdate(roomId, dateForLock).orElseThrow();
                        }
                    });

            if (!daily.canAllocate(count)) {
                throw new DomainValidationException(
                        "Insufficient room inventory on " + dateForLock + ". Available: " + daily.getAvailableRooms() + ", Requested: " + count);
            }

            double nightlyRate = pricingService.resolveEffectivePrice(roomType, dateForLock);
            totalNightlySum += nightlyRate;
            ratesBreakdown.add(Map.of("date", dateForLock.toString(), "rate", nightlyRate));

            lockedInventories.add(daily);
            cur = cur.plusDays(1);
        }

        // Step 2: Atomic allocation update across all locked nights
        for (RoomDailyInventory inv : lockedInventories) {
            inv.setHeldRooms(inv.getHeldRooms() + count);
            dailyInventoryRepository.saveAndFlush(inv);
        }

        double finalTotalPrice = totalNightlySum * count;
        String ratesJson = "";
        try {
            ratesJson = objectMapper.writeValueAsString(ratesBreakdown);
        } catch (JsonProcessingException ignored) {}

        // Step 3: Record booking allocation
        HotelBookingAllocation allocation = HotelBookingAllocation.builder()
                .bookingRef(dto.getBookingRef())
                .hotelId(roomType.getHotelId())
                .roomId(roomId)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .roomCount(count)
                .status("HELD")
                .totalPrice(finalTotalPrice)
                .nightlyRatesJson(ratesJson)
                .build();

        HotelBookingAllocation savedAlloc = allocationRepository.save(allocation);

        updateLegacyAvailableSnapshot(roomType);

        log.info("Reserved {} room(s) for roomId {} across stay [{} to {}) under bookingRef {}",
                count, roomId, checkIn, checkOut, dto.getBookingRef());

        return Map.of(
                "message", "Room stay inventory reserved successfully",
                "allocationId", savedAlloc.getId(),
                "bookingRef", savedAlloc.getBookingRef(),
                "roomId", roomId,
                "checkInDate", checkIn.toString(),
                "checkOutDate", checkOut.toString(),
                "roomCount", count,
                "status", savedAlloc.getStatus(),
                "totalPrice", finalTotalPrice
        );
    }

    /**
     * Authoritatively and idempotently releases room inventory.
     * Restores held or confirmed inventory across all nights of the stay.
     */
    @Transactional
    public Map<String, Object> releaseStayInventory(
            String bookingRef, CallerPrincipal principal, boolean isInternal) {

        if (bookingRef == null || bookingRef.isBlank()) {
            throw new DomainValidationException("Booking reference is mandatory for release");
        }

        Optional<HotelBookingAllocation> allocOpt = allocationRepository.findByBookingRef(bookingRef);
        if (allocOpt.isEmpty()) {
            log.warn("No active allocation found for bookingRef {} during release. Returning idempotent 200.", bookingRef);
            return Map.of(
                    "message", "No allocation found for booking reference or already released",
                    "bookingRef", bookingRef,
                    "status", "RELEASED",
                    "idempotent", true
            );
        }

        HotelBookingAllocation alloc = allocOpt.get();

        if ("RELEASED".equalsIgnoreCase(alloc.getStatus())) {
            log.info("BookingRef {} already has status RELEASED. Returning idempotent 200.", bookingRef);
            return Map.of(
                    "message", "Inventory already released for booking reference",
                    "bookingRef", bookingRef,
                    "status", "RELEASED",
                    "idempotent", true
            );
        }

        if (!isInternal) {
            if (principal == null) {
                throw new OwnershipAccessDeniedException("Authentication required to release inventory");
            }
            if (principal.isHotelManager()) {
                HotelProperty hotel = hotelRepository.findById(alloc.getHotelId())
                        .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found"));
                if (!principal.userId().equals(hotel.getManagerId())) {
                    throw new OwnershipAccessDeniedException("Access denied: Cannot release inventory for a property you do not own");
                }
            } else if (!principal.isAdmin()) {
                throw new OwnershipAccessDeniedException("Access denied: Only trusted internal services, admins, or the owning manager can release inventory");
            }
        }

        LocalDate cur = alloc.getCheckInDate();
        while (cur.isBefore(alloc.getCheckOutDate())) {
            final LocalDate dateForLock = cur;
            Optional<RoomDailyInventory> dailyOpt = dailyInventoryRepository
                    .findByRoomIdAndInventoryDateForUpdate(alloc.getRoomId(), dateForLock);

            if (dailyOpt.isPresent()) {
                RoomDailyInventory daily = dailyOpt.get();
                if ("HELD".equalsIgnoreCase(alloc.getStatus())) {
                    daily.setHeldRooms(Math.max(0, daily.getHeldRooms() - alloc.getRoomCount()));
                } else if ("CONFIRMED".equalsIgnoreCase(alloc.getStatus())) {
                    daily.setBookedRooms(Math.max(0, daily.getBookedRooms() - alloc.getRoomCount()));
                }
                dailyInventoryRepository.save(daily);
            }
            cur = cur.plusDays(1);
        }

        alloc.setStatus("RELEASED");
        alloc.setUpdatedAt(LocalDateTime.now());
        allocationRepository.save(alloc);

        roomTypeRepository.findById(alloc.getRoomId()).ifPresent(this::updateLegacyAvailableSnapshot);

        log.info("Released {} room(s) for roomId {} across stay [{} to {}) under bookingRef {}",
                alloc.getRoomCount(), alloc.getRoomId(), alloc.getCheckInDate(), alloc.getCheckOutDate(), bookingRef);

        return Map.of(
                "message", "Stay inventory released successfully",
                "bookingRef", bookingRef,
                "roomId", alloc.getRoomId(),
                "releasedRooms", alloc.getRoomCount(),
                "status", "RELEASED"
        );
    }

    /**
     * Confirms a held reservation upon successful payment capture.
     * Transitions allocation from HELD to CONFIRMED and daily inventory from heldRooms to bookedRooms.
     */
    @Transactional
    public Map<String, Object> confirmStayInventory(String bookingRef, CallerPrincipal principal, boolean isInternal) {
        if (bookingRef == null || bookingRef.isBlank()) {
            throw new DomainValidationException("Booking reference is mandatory for confirmation");
        }

        HotelBookingAllocation alloc = allocationRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> new ResourceNotFoundException("No allocation found for bookingRef: " + bookingRef));

        if ("CONFIRMED".equalsIgnoreCase(alloc.getStatus())) {
            return Map.of(
                    "message", "Reservation already confirmed",
                    "bookingRef", bookingRef,
                    "status", "CONFIRMED",
                    "idempotent", true
            );
        }

        if (!"HELD".equalsIgnoreCase(alloc.getStatus())) {
            throw new DomainValidationException("Cannot confirm allocation with status: " + alloc.getStatus());
        }

        LocalDate cur = alloc.getCheckInDate();
        while (cur.isBefore(alloc.getCheckOutDate())) {
            final LocalDate confirmDate = cur;
            RoomDailyInventory daily = dailyInventoryRepository
                    .findByRoomIdAndInventoryDateForUpdate(alloc.getRoomId(), confirmDate)
                    .orElseGet(() -> getOrCreateDailyInventory(
                            roomTypeRepository.findById(alloc.getRoomId()).orElseThrow(), confirmDate));

            daily.setHeldRooms(Math.max(0, daily.getHeldRooms() - alloc.getRoomCount()));
            daily.setBookedRooms(daily.getBookedRooms() + alloc.getRoomCount());
            dailyInventoryRepository.save(daily);
            cur = cur.plusDays(1);
        }

        alloc.setStatus("CONFIRMED");
        alloc.setUpdatedAt(LocalDateTime.now());
        allocationRepository.save(alloc);

        log.info("Confirmed stay allocation for bookingRef {} ({} rooms)", bookingRef, alloc.getRoomCount());

        return Map.of(
                "message", "Stay allocation confirmed successfully",
                "bookingRef", bookingRef,
                "status", "CONFIRMED"
        );
    }

    /**
     * Validates room capacity reductions against future committed allocations (BD-07).
     * Rejects reduction if any future date has committed rooms (booked + held + blocked) > newTotalRooms.
     */
    @Transactional
    public void validateAndApplyCapacityChange(RoomType roomType, int newTotalRooms) {
        if (newTotalRooms < 0) {
            throw new DomainValidationException("Total rooms cannot be negative");
        }

        int currentTotal = roomType.getTotalRooms() != null ? roomType.getTotalRooms() : 0;

        if (newTotalRooms < currentTotal) {
            List<RoomDailyInventory> futureInventories = dailyInventoryRepository
                    .findByRoomIdAndInventoryDateGreaterThanEqualOrderByInventoryDateAsc(
                            roomType.getId(), LocalDate.now());

            for (RoomDailyInventory inv : futureInventories) {
                int committed = inv.getTotalAllocated();
                if (committed > newTotalRooms) {
                    throw new DomainValidationException(
                            "Cannot reduce room capacity to " + newTotalRooms + ". Existing allocations of "
                                    + committed + " (booked: " + inv.getBookedRooms() + ", held: " + inv.getHeldRooms()
                                    + ", blocked: " + inv.getBlockedRooms() + ") exist on date " + inv.getInventoryDate() + " (BD-07)");
                }
            }

            for (RoomDailyInventory inv : futureInventories) {
                inv.setTotalRooms(newTotalRooms);
                dailyInventoryRepository.save(inv);
            }
        } else if (newTotalRooms > currentTotal) {
            List<RoomDailyInventory> futureInventories = dailyInventoryRepository
                    .findByRoomIdAndInventoryDateGreaterThanEqualOrderByInventoryDateAsc(
                            roomType.getId(), LocalDate.now());
            for (RoomDailyInventory inv : futureInventories) {
                inv.setTotalRooms(newTotalRooms);
                dailyInventoryRepository.save(inv);
            }
        }
    }

    /**
     * Returns daily inventory calendar summary for a given date range.
     */
    @Transactional
    public List<DailyInventoryDto> getDailyInventoryCalendar(
            Long roomId, LocalDate startDate, LocalDate endDate, CallerPrincipal principal) {

        RoomType roomType = roomTypeRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room category not found with ID: " + roomId));

        if (startDate == null) startDate = LocalDate.now();
        if (endDate == null) endDate = startDate.plusDays(30);

        if (startDate.isAfter(endDate)) {
            throw new DomainValidationException("Start date must not be after end date");
        }

        List<DailyInventoryDto> result = new ArrayList<>();
        LocalDate cur = startDate;
        while (!cur.isAfter(endDate)) {
            RoomDailyInventory daily = getOrCreateDailyInventory(roomType, cur);
            double effectivePrice = pricingService.resolveEffectivePrice(roomType, cur);

            result.add(DailyInventoryDto.builder()
                    .date(cur)
                    .totalRooms(daily.getTotalRooms())
                    .bookedRooms(daily.getBookedRooms())
                    .heldRooms(daily.getHeldRooms())
                    .blockedRooms(daily.getBlockedRooms())
                    .availableRooms(daily.getAvailableRooms())
                    .effectivePrice(effectivePrice)
                    .build());

            cur = cur.plusDays(1);
        }

        return result;
    }

    /**
     * Aggregates date-wise hotel inventory across all room types for a reporting period (WP-03/WP-05).
     * Calculates total physical room nights, occupied room nights, held, blocked, available, and true occupancy.
     */
    public HotelInventorySummaryDto getHotelInventorySummary(
            Long hotelId, LocalDate startDate, LocalDate endDate, CallerPrincipal principal) {

        HotelProperty hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found with ID: " + hotelId));

        if (principal != null && !principal.isAdmin() && (principal.userId() == null || !principal.userId().equals(hotel.getManagerId()))) {
            throw new OwnershipAccessDeniedException("Access denied: You do not own hotel property " + hotelId);
        }

        if (startDate == null) startDate = LocalDate.now();
        if (endDate == null) endDate = startDate.plusDays(6);

        if (startDate.isAfter(endDate)) {
            throw new DomainValidationException("Start date must not be after end date");
        }

        List<RoomType> roomTypes = roomTypeRepository.findByHotelId(hotelId);

        int totalPhysicalNights = 0;
        int occupiedNights = 0;
        int heldNights = 0;
        int blockedNights = 0;
        int availableNights = 0;

        List<HotelInventorySummaryDto.DailySummaryItem> dailySummaries = new ArrayList<>();
        LocalDate cur = startDate;

        while (!cur.isAfter(endDate)) {
            int dayTotal = 0;
            int dayBooked = 0;
            int dayHeld = 0;
            int dayBlocked = 0;
            int dayAvailable = 0;

            for (RoomType rt : roomTypes) {
                RoomDailyInventory daily = getOrCreateDailyInventory(rt, cur);
                dayTotal += (daily.getTotalRooms() != null ? daily.getTotalRooms() : 0);
                dayBooked += (daily.getBookedRooms() != null ? daily.getBookedRooms() : 0);
                dayHeld += (daily.getHeldRooms() != null ? daily.getHeldRooms() : 0);
                dayBlocked += (daily.getBlockedRooms() != null ? daily.getBlockedRooms() : 0);
                dayAvailable += daily.getAvailableRooms();
            }

            int dayApplicable = Math.max(0, dayTotal - dayBlocked);
            double dayOccupancy = dayApplicable > 0
                    ? Math.round(((double) dayBooked / dayApplicable) * 1000.0) / 10.0
                    : 0.0;

            dailySummaries.add(HotelInventorySummaryDto.DailySummaryItem.builder()
                    .date(cur)
                    .totalRooms(dayTotal)
                    .bookedRooms(dayBooked)
                    .heldRooms(dayHeld)
                    .blockedRooms(dayBlocked)
                    .availableRooms(dayAvailable)
                    .occupancyRate(dayOccupancy)
                    .build());

            totalPhysicalNights += dayTotal;
            occupiedNights += dayBooked;
            heldNights += dayHeld;
            blockedNights += dayBlocked;
            availableNights += dayAvailable;

            cur = cur.plusDays(1);
        }

        int applicableCapacity = Math.max(0, totalPhysicalNights - blockedNights);
        double overallOccupancy = applicableCapacity > 0
                ? Math.round(((double) occupiedNights / applicableCapacity) * 1000.0) / 10.0
                : 0.0;

        return HotelInventorySummaryDto.builder()
                .hotelId(hotelId)
                .startDate(startDate)
                .endDate(endDate)
                .totalPhysicalRoomNights(totalPhysicalNights)
                .occupiedRoomNights(occupiedNights)
                .heldRoomNights(heldNights)
                .blockedRoomNights(blockedNights)
                .availableRoomNights(availableNights)
                .applicableRoomNightCapacity(applicableCapacity)
                .occupancyRate(overallOccupancy)
                .dailySummaries(dailySummaries)
                .build();
    }

    /**
     * Synchronizes legacy availableRooms scalar on RoomType to reflect current minimum upcoming availability.
     */
    public void updateLegacyAvailableSnapshot(RoomType roomType) {
        try {
            LocalDate today = LocalDate.now();
            RoomDailyInventory todayInv = dailyInventoryRepository
                    .findByRoomIdAndInventoryDate(roomType.getId(), today)
                    .orElse(null);

            if (todayInv != null) {
                roomType.setAvailableRooms(todayInv.getAvailableRooms());
                roomTypeRepository.save(roomType);
            }
        } catch (Exception e) {
            log.debug("Could not synchronize legacy availableRooms snapshot: {}", e.getMessage());
        }
    }
}
