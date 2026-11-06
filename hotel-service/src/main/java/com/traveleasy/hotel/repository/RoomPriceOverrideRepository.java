package com.traveleasy.hotel.repository;

import com.traveleasy.hotel.model.RoomPriceOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomPriceOverrideRepository extends JpaRepository<RoomPriceOverride, Long> {

    Optional<RoomPriceOverride> findByRoomIdAndOverrideDate(Long roomId, LocalDate overrideDate);

    List<RoomPriceOverride> findByRoomIdAndOverrideDateBetweenOrderByOverrideDateAsc(
            Long roomId, LocalDate startDate, LocalDate endDate);

    void deleteByRoomIdAndOverrideDate(Long roomId, LocalDate overrideDate);
}
