package com.traveleasy.hotel.service;

import com.traveleasy.hotel.dto.RoomBlockRequestDto;
import com.traveleasy.hotel.dto.RoomBlockResponseDto;
import com.traveleasy.hotel.exception.DomainValidationException;
import com.traveleasy.hotel.exception.OwnershipAccessDeniedException;
import com.traveleasy.hotel.exception.ResourceNotFoundException;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomDailyInventory;
import com.traveleasy.hotel.model.RoomInventoryBlock;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.RoomDailyInventoryRepository;
import com.traveleasy.hotel.repository.RoomInventoryBlockRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import com.traveleasy.hotel.security.HotelSecurityService.CallerPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomBlockService {

    private final RoomInventoryBlockRepository blockRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final HotelPropertyRepository hotelRepository;
    private final RoomDailyInventoryRepository dailyInventoryRepository;
    private final HotelInventoryService inventoryService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private static final Set<String> ALLOWED_REASONS = Set.of(
            "MAINTENANCE", "RENOVATION", "OPERATIONAL", "OTHER"
    );

    @Transactional
    public RoomBlockResponseDto createBlock(
            Long hotelId, Long roomId, RoomBlockRequestDto dto, CallerPrincipal principal) {

        HotelProperty hotel = validateOwnershipAndGetHotel(hotelId, principal);
        RoomType roomType = roomTypeRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room category not found with ID: " + roomId));

        if (!hotelId.equals(roomType.getHotelId())) {
            throw new DomainValidationException("Room category " + roomId + " does not belong to hotel property " + hotelId);
        }

        // 1. Mandatory Reason validation (BD-10)
        if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
            throw new DomainValidationException("A blocking reason is mandatory (BD-10)");
        }
        String normalizedReason = dto.getReason().trim().toUpperCase();
        if (!ALLOWED_REASONS.contains(normalizedReason)) {
            throw new DomainValidationException("Invalid blocking reason. Allowed values: MAINTENANCE, RENOVATION, OPERATIONAL, OTHER");
        }

        // 2. Date range validation
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new DomainValidationException("Start date and end date are mandatory for blocking");
        }
        if (!dto.getEndDate().isAfter(dto.getStartDate())) {
            throw new DomainValidationException("End date must be strictly after start date");
        }
        if (dto.getStartDate().isBefore(LocalDate.now())) {
            throw new DomainValidationException("Cannot block room inventory for past dates");
        }

        // 3. Block quantity validation
        if (dto.getBlockedCount() == null || dto.getBlockedCount() <= 0) {
            throw new DomainValidationException("Blocked room count must be at least 1");
        }
        int count = dto.getBlockedCount();

        // 4. Check availability and acquire pessimistic locks across all nights
        List<RoomDailyInventory> lockedInventories = new ArrayList<>();
        LocalDate cur = dto.getStartDate();
        while (cur.isBefore(dto.getEndDate())) {
            final LocalDate dateForLock = cur;
            RoomDailyInventory daily = dailyInventoryRepository
                    .findByRoomIdAndInventoryDateForUpdate(roomId, dateForLock)
                    .orElseGet(() -> {
                        RoomDailyInventory newInv = RoomDailyInventory.builder()
                                .hotelId(hotelId)
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
                        "Cannot block rooms: Insufficient available inventory on " + dateForLock
                                + " (Available: " + daily.getAvailableRooms() + ", Requested block: " + count + ")");
            }
            lockedInventories.add(daily);
            cur = cur.plusDays(1);
        }

        // 5. Apply block count
        for (RoomDailyInventory inv : lockedInventories) {
            inv.setBlockedRooms(inv.getBlockedRooms() + count);
            dailyInventoryRepository.saveAndFlush(inv);
        }

        // 7. Save block record
        RoomInventoryBlock block = RoomInventoryBlock.builder()
                .hotelId(hotelId)
                .roomId(roomId)
                .managerId(principal != null ? principal.userId() : hotel.getManagerId())
                .blockedCount(count)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .reason(normalizedReason)
                .notes(dto.getNotes())
                .status("ACTIVE")
                .build();

        RoomInventoryBlock saved = blockRepository.save(block);
        inventoryService.updateLegacyAvailableSnapshot(roomType);

        log.info("Created room block ID {} for roomId {} (count: {}, dates: [{} to {}), reason: {}) by user {}",
                saved.getId(), roomId, count, dto.getStartDate(), dto.getEndDate(), normalizedReason,
                principal != null ? principal.userId() : "system");

        return toResponseDto(saved, roomType.getRoomTypeName());
    }

    @Transactional
    public RoomBlockResponseDto releaseBlock(Long blockId, CallerPrincipal principal) {
        RoomInventoryBlock block = blockRepository.findById(blockId)
                .orElseThrow(() -> new ResourceNotFoundException("Room block record not found with ID: " + blockId));

        validateOwnershipAndGetHotel(block.getHotelId(), principal);

        RoomType roomType = roomTypeRepository.findById(block.getRoomId()).orElse(null);
        String roomTypeName = roomType != null ? roomType.getRoomTypeName() : "Room";

        if ("RELEASED".equalsIgnoreCase(block.getStatus())) {
            log.info("Block ID {} is already RELEASED. Returning existing block.", blockId);
            return toResponseDto(block, roomTypeName);
        }

        // Restore blocked rooms across all nights
        LocalDate cur = block.getStartDate();
        while (cur.isBefore(block.getEndDate())) {
            Optional<RoomDailyInventory> dailyOpt = dailyInventoryRepository
                    .findByRoomIdAndInventoryDateForUpdate(block.getRoomId(), cur);
            if (dailyOpt.isPresent()) {
                RoomDailyInventory daily = dailyOpt.get();
                daily.setBlockedRooms(Math.max(0, daily.getBlockedRooms() - block.getBlockedCount()));
                dailyInventoryRepository.save(daily);
            }
            cur = cur.plusDays(1);
        }

        block.setStatus("RELEASED");
        block.setReleasedAt(LocalDateTime.now());
        RoomInventoryBlock saved = blockRepository.save(block);

        if (roomType != null) {
            inventoryService.updateLegacyAvailableSnapshot(roomType);
        }

        log.info("Released room block ID {} for roomId {} by user {}",
                blockId, block.getRoomId(), principal != null ? principal.userId() : "system");

        return toResponseDto(saved, roomTypeName);
    }

    public List<RoomBlockResponseDto> getBlocksByHotel(Long hotelId, CallerPrincipal principal) {
        validateOwnershipAndGetHotel(hotelId, principal);

        List<RoomInventoryBlock> blocks = blockRepository.findByHotelIdOrderByCreatedAtDesc(hotelId);
        List<RoomBlockResponseDto> result = new ArrayList<>();
        Map<Long, String> roomNames = new HashMap<>();

        for (RoomInventoryBlock b : blocks) {
            String name = roomNames.computeIfAbsent(b.getRoomId(), id ->
                    roomTypeRepository.findById(id).map(RoomType::getRoomTypeName).orElse("Room " + id));
            result.add(toResponseDto(b, name));
        }
        return result;
    }

    public List<RoomBlockResponseDto> getBlocksByRoom(Long roomId, CallerPrincipal principal) {
        RoomType roomType = roomTypeRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room category not found with ID: " + roomId));
        validateOwnershipAndGetHotel(roomType.getHotelId(), principal);

        List<RoomInventoryBlock> blocks = blockRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
        List<RoomBlockResponseDto> result = new ArrayList<>();
        for (RoomInventoryBlock b : blocks) {
            result.add(toResponseDto(b, roomType.getRoomTypeName()));
        }
        return result;
    }

    private HotelProperty validateOwnershipAndGetHotel(Long hotelId, CallerPrincipal principal) {
        HotelProperty hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found with ID: " + hotelId));

        if (principal == null || principal.isAdmin()) {
            return hotel;
        }

        if (principal.isHotelManager()) {
            if (!principal.userId().equals(hotel.getManagerId())) {
                throw new OwnershipAccessDeniedException("Access denied: You do not own this hotel property");
            }
            return hotel;
        }

        throw new OwnershipAccessDeniedException("Access denied: Hotel Manager or Admin role required");
    }

    private RoomBlockResponseDto toResponseDto(RoomInventoryBlock b, String roomTypeName) {
        return RoomBlockResponseDto.builder()
                .id(b.getId())
                .hotelId(b.getHotelId())
                .roomId(b.getRoomId())
                .roomTypeName(roomTypeName)
                .managerId(b.getManagerId())
                .blockedCount(b.getBlockedCount())
                .startDate(b.getStartDate())
                .endDate(b.getEndDate())
                .reason(b.getReason())
                .notes(b.getNotes())
                .status(b.getStatus())
                .createdAt(b.getCreatedAt())
                .releasedAt(b.getReleasedAt())
                .build();
    }
}
