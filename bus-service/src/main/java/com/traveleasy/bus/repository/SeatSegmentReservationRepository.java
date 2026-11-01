package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.SeatSegmentReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SeatSegmentReservationRepository extends JpaRepository<SeatSegmentReservation, Long> {

    List<SeatSegmentReservation> findByScheduleIdOrderBySeatNumberAscSegmentIndexAsc(Long scheduleId);

    List<SeatSegmentReservation> findByScheduleIdAndBookingRef(Long scheduleId, String bookingRef);

    List<SeatSegmentReservation> findByBookingRef(String bookingRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SeatSegmentReservation r " +
           "WHERE r.scheduleId = :scheduleId " +
           "AND r.seatNumber IN :seats " +
           "AND r.segmentIndex >= :fromSeg " +
           "AND r.segmentIndex < :toSeg " +
           "AND (r.status = 'BOOKED' OR (r.status = 'HELD' AND (r.holdExpiresAt IS NULL OR r.holdExpiresAt > :now))) " +
           "ORDER BY r.seatNumber ASC, r.segmentIndex ASC")
    List<SeatSegmentReservation> findConflictingReservations(
            @Param("scheduleId") Long scheduleId,
            @Param("seats") List<String> seats,
            @Param("fromSeg") Integer fromSeg,
            @Param("toSeg") Integer toSeg,
            @Param("now") LocalDateTime now);

    @Query("SELECT r FROM SeatSegmentReservation r " +
           "WHERE r.scheduleId = :scheduleId " +
           "AND r.segmentIndex >= :fromSeg " +
           "AND r.segmentIndex < :toSeg " +
           "AND (r.status = 'BOOKED' OR (r.status = 'HELD' AND (r.holdExpiresAt IS NULL OR r.holdExpiresAt > :now)))")
    List<SeatSegmentReservation> findActiveByScheduleAndSegmentRange(
            @Param("scheduleId") Long scheduleId,
            @Param("fromSeg") Integer fromSeg,
            @Param("toSeg") Integer toSeg,
            @Param("now") LocalDateTime now);

    @Query("SELECT r FROM SeatSegmentReservation r " +
           "WHERE r.status = 'HELD' AND r.holdExpiresAt <= :now " +
           "ORDER BY r.scheduleId ASC, r.seatNumber ASC, r.segmentIndex ASC")
    List<SeatSegmentReservation> findExpiredHolds(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SeatSegmentReservation r WHERE r.scheduleId = :scheduleId")
    void deleteByScheduleId(@Param("scheduleId") Long scheduleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SeatSegmentReservation r WHERE r.bookingRef = :bookingRef")
    void deleteByBookingRef(@Param("bookingRef") String bookingRef);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SeatSegmentReservation r WHERE r.scheduleId = :scheduleId AND r.bookingRef = :bookingRef")
    void deleteByScheduleIdAndBookingRef(@Param("scheduleId") Long scheduleId, @Param("bookingRef") String bookingRef);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SeatSegmentReservation r WHERE r.id IN :ids")
    void deleteByIds(@Param("ids") List<Long> ids);
}
