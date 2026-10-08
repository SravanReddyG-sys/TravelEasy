package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByScheduleId(Long scheduleId);
    Optional<Seat> findByScheduleIdAndSeatNumber(Long scheduleId, String seatNumber);
}
