package com.traveleasy.hotel.service;

import com.traveleasy.hotel.dto.BasePriceUpdateRequestDto;
import com.traveleasy.hotel.dto.PriceCalendarEntryDto;
import com.traveleasy.hotel.dto.PriceOverrideRequestDto;
import com.traveleasy.hotel.dto.PriceRangeOverrideRequestDto;
import com.traveleasy.hotel.dto.RoomTypeDto;
import com.traveleasy.hotel.dto.StayAvailabilityResponseDto.NightlyRateDto;
import com.traveleasy.hotel.exception.DomainValidationException;
import com.traveleasy.hotel.exception.OwnershipAccessDeniedException;
import com.traveleasy.hotel.exception.ResourceNotFoundException;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomPriceOverride;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.RoomPriceOverrideRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import com.traveleasy.hotel.security.HotelSecurityService.CallerPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelPricingService {

    private final RoomTypeRepository roomTypeRepository;
    private final HotelPropertyRepository hotelRepository;
    private final RoomPriceOverrideRepository priceOverrideRepository;

    public double resolveEffectivePrice(RoomType roomType, LocalDate date) {
        if (roomType == null || date == null) {
            return 0.0;
        }
        Optional<RoomPriceOverride> override = priceOverrideRepository.findByRoomIdAndOverrideDate(roomType.getId(), date);
        if (override.isPresent() && override.get().getPricePerNight() != null) {
            return override.get().getPricePerNight();
        }
        return roomType.getPricePerNight() != null ? roomType.getPricePerNight() : 0.0;
    }

    public boolean isOverridePresent(Long roomId, LocalDate date) {
        return priceOverrideRepository.findByRoomIdAndOverrideDate(roomId, date).isPresent();
    }

    public Map<LocalDate, Double> resolveEffectivePricesForRange(RoomType roomType, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, Double> prices = new LinkedHashMap<>();
        if (roomType == null || startDate == null || endDate == null) {
            return prices;
        }

        List<RoomPriceOverride> overrides = priceOverrideRepository
                .findByRoomIdAndOverrideDateBetweenOrderByOverrideDateAsc(roomType.getId(), startDate, endDate);
        Map<LocalDate, Double> overrideMap = new HashMap<>();
        for (RoomPriceOverride o : overrides) {
            overrideMap.put(o.getOverrideDate(), o.getPricePerNight());
        }

        double base = roomType.getPricePerNight() != null ? roomType.getPricePerNight() : 0.0;
        LocalDate cur = startDate;
        while (!cur.isAfter(endDate)) {
            prices.put(cur, overrideMap.getOrDefault(cur, base));
            cur = cur.plusDays(1);
        }
        return prices;
    }

    @Transactional
    public PriceCalendarEntryDto setPriceOverride(Long roomId, PriceOverrideRequestDto dto, CallerPrincipal principal) {
        RoomType roomType = validateOwnershipAndGetRoom(roomId, principal);

        if (dto.getOverrideDate() == null) {
            throw new DomainValidationException("Override date is mandatory");
        }
        if (dto.getPricePerNight() == null || dto.getPricePerNight() <= 0
                || Double.isNaN(dto.getPricePerNight()) || Double.isInfinite(dto.getPricePerNight())) {
            throw new DomainValidationException("Price per night must be positive and greater than zero");
        }

        double roundedPrice = Math.round(dto.getPricePerNight() * 100.0) / 100.0;

        RoomPriceOverride override = priceOverrideRepository
                .findByRoomIdAndOverrideDate(roomId, dto.getOverrideDate())
                .orElse(RoomPriceOverride.builder()
                        .hotelId(roomType.getHotelId())
                        .roomId(roomId)
                        .overrideDate(dto.getOverrideDate())
                        .build());

        override.setPricePerNight(roundedPrice);
        RoomPriceOverride saved = priceOverrideRepository.save(override);

        log.info("Set price override for roomId {} on {} to {} by user {}",
                roomId, dto.getOverrideDate(), roundedPrice, principal != null ? principal.userId() : "system");

        return PriceCalendarEntryDto.builder()
                .date(saved.getOverrideDate())
                .basePrice(roomType.getPricePerNight())
                .overridePrice(saved.getPricePerNight())
                .effectivePrice(saved.getPricePerNight())
                .isOverride(true)
                .build();
    }

    @Transactional
    public List<PriceCalendarEntryDto> setRangePriceOverrides(Long roomId, PriceRangeOverrideRequestDto dto, CallerPrincipal principal) {
        RoomType roomType = validateOwnershipAndGetRoom(roomId, principal);

        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new DomainValidationException("Start date and end date are mandatory");
        }
        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new DomainValidationException("Start date must not be after end date");
        }
        if (dto.getPricePerNight() == null || dto.getPricePerNight() <= 0
                || Double.isNaN(dto.getPricePerNight()) || Double.isInfinite(dto.getPricePerNight())) {
            throw new DomainValidationException("Price per night must be positive and greater than zero");
        }

        double roundedPrice = Math.round(dto.getPricePerNight() * 100.0) / 100.0;

        List<PriceCalendarEntryDto> results = new ArrayList<>();
        LocalDate cur = dto.getStartDate();
        while (!cur.isAfter(dto.getEndDate())) {
            RoomPriceOverride override = priceOverrideRepository
                    .findByRoomIdAndOverrideDate(roomId, cur)
                    .orElse(RoomPriceOverride.builder()
                            .hotelId(roomType.getHotelId())
                            .roomId(roomId)
                            .overrideDate(cur)
                            .build());

            override.setPricePerNight(roundedPrice);
            RoomPriceOverride saved = priceOverrideRepository.save(override);
            results.add(PriceCalendarEntryDto.builder()
                    .date(saved.getOverrideDate())
                    .basePrice(roomType.getPricePerNight())
                    .overridePrice(saved.getPricePerNight())
                    .effectivePrice(saved.getPricePerNight())
                    .isOverride(true)
                    .build());
            cur = cur.plusDays(1);
        }

        log.info("Set range price overrides for roomId {} from {} to {} to {} by user {}",
                roomId, dto.getStartDate(), dto.getEndDate(), roundedPrice, principal != null ? principal.userId() : "system");

        return results;
    }

    @Transactional
    public Map<String, Object> deletePriceOverride(Long roomId, LocalDate date, CallerPrincipal principal) {
        RoomType roomType = validateOwnershipAndGetRoom(roomId, principal);

        if (date == null) {
            throw new DomainValidationException("Date is mandatory to remove price override");
        }

        priceOverrideRepository.deleteByRoomIdAndOverrideDate(roomId, date);

        log.info("Deleted price override for roomId {} on {} by user {}. Reverted to base price {}",
                roomId, date, principal != null ? principal.userId() : "system", roomType.getPricePerNight());

        return Map.of(
                "message", "Price override removed successfully. Base price restored.",
                "roomId", roomId,
                "date", date.toString(),
                "basePrice", roomType.getPricePerNight(),
                "effectivePrice", roomType.getPricePerNight()
        );
    }

    public List<PriceCalendarEntryDto> getPriceCalendar(Long roomId, LocalDate startDate, LocalDate endDate, CallerPrincipal principal) {
        RoomType roomType = validateOwnershipAndGetRoom(roomId, principal);

        if (startDate == null) startDate = LocalDate.now();
        if (endDate == null) endDate = startDate.plusDays(30);
        if (startDate.isAfter(endDate)) {
            throw new DomainValidationException("Start date must not be after end date");
        }

        List<RoomPriceOverride> overrides = priceOverrideRepository
                .findByRoomIdAndOverrideDateBetweenOrderByOverrideDateAsc(roomId, startDate, endDate);
        Map<LocalDate, Double> overrideMap = new HashMap<>();
        for (RoomPriceOverride o : overrides) {
            overrideMap.put(o.getOverrideDate(), o.getPricePerNight());
        }

        List<PriceCalendarEntryDto> calendar = new ArrayList<>();
        LocalDate cur = startDate;
        while (!cur.isAfter(endDate)) {
            boolean isOverride = overrideMap.containsKey(cur);
            Double overridePrice = isOverride ? overrideMap.get(cur) : null;
            double effectivePrice = (isOverride && overridePrice != null) ? overridePrice : (roomType.getPricePerNight() != null ? roomType.getPricePerNight() : 0.0);

            calendar.add(PriceCalendarEntryDto.builder()
                    .date(cur)
                    .basePrice(roomType.getPricePerNight())
                    .overridePrice(overridePrice)
                    .effectivePrice(effectivePrice)
                    .isOverride(isOverride)
                    .build());
            cur = cur.plusDays(1);
        }
        return calendar;
    }

    @Transactional
    public RoomTypeDto updateBasePrice(Long roomId, BasePriceUpdateRequestDto dto, CallerPrincipal principal) {
        if (principal == null) {
            throw new OwnershipAccessDeniedException("Access denied: Authentication required");
        }

        RoomType roomType = validateOwnershipAndGetRoom(roomId, principal);

        if (dto == null || dto.getBasePrice() == null) {
            throw new DomainValidationException("Base price is mandatory");
        }
        if (dto.getBasePrice() <= 0 || Double.isNaN(dto.getBasePrice()) || Double.isInfinite(dto.getBasePrice())) {
            throw new DomainValidationException("Base price must be a valid positive number greater than zero");
        }

        double roundedPrice = Math.round(dto.getBasePrice() * 100.0) / 100.0;
        roomType.setPricePerNight(roundedPrice);
        RoomType saved = roomTypeRepository.save(roomType);

        log.info("Updated base price for roomId {} to {} by user {}",
                roomId, roundedPrice, principal.userId());

        return RoomTypeDto.fromEntity(saved);
    }

    private RoomType validateOwnershipAndGetRoom(Long roomId, CallerPrincipal principal) {
        RoomType roomType = roomTypeRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room category not found with ID: " + roomId));

        if ("ARCHIVED".equalsIgnoreCase(roomType.getStatus())) {
            throw new DomainValidationException("Operational pricing management is not permitted for an archived room category");
        }

        if (principal == null || principal.isAdmin()) {
            return roomType;
        }

        if (principal.isHotelManager()) {
            HotelProperty hotel = hotelRepository.findById(roomType.getHotelId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found"));
            if (!principal.userId().equals(hotel.getManagerId())) {
                throw new OwnershipAccessDeniedException("Access denied: You do not own the hotel property for this room category");
            }
            return roomType;
        }

        throw new OwnershipAccessDeniedException("Access denied: Hotel Manager or Admin role required");
    }
}
