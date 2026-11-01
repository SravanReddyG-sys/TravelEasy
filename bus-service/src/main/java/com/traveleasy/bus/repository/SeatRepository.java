package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByScheduleId(Long scheduleId);
    Optional<Seat> findByScheduleIdAndSeatNumber(Long scheduleId, String seatNumber);
    List<Seat> findByStatusInAndHoldExpiresAtBefore(List<String> statuses, java.time.LocalDateTime expiryThreshold);
    List<Seat> findByHoldBookingRef(String holdBookingRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.scheduleId = :scheduleId ORDER BY s.seatNumber ASC")
    List<Seat> findByScheduleIdWithLock(@Param("scheduleId") Long scheduleId);
}
