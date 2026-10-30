package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.BusSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BusScheduleRepository extends JpaRepository<BusSchedule, Long> {
    @Query("SELECT s FROM BusSchedule s WHERE LOWER(s.origin) = LOWER(:origin) AND LOWER(s.destination) = LOWER(:destination) AND s.travelDate = :travelDate AND (s.status IS NULL OR UPPER(s.status) != 'CANCELLED')")
    List<BusSchedule> findByOriginIgnoreCaseAndDestinationIgnoreCaseAndTravelDate(@Param("origin") String origin, @Param("destination") String destination, @Param("travelDate") LocalDate travelDate);

    @Query("SELECT DISTINCT s FROM BusSchedule s " +
           "WHERE ((LOWER(s.origin) = LOWER(:origin) AND LOWER(s.destination) = LOWER(:destination)) " +
           "   OR s.id IN (" +
           "       SELECT s1.scheduleId FROM ScheduleStop s1, ScheduleStop s2 " +
           "       WHERE s1.scheduleId = s2.scheduleId " +
           "         AND LOWER(s1.stopName) = LOWER(:origin) " +
           "         AND LOWER(s2.stopName) = LOWER(:destination) " +
           "         AND s1.stopOrder < s2.stopOrder" +
           "   )) " +
           "  AND s.travelDate = :travelDate " +
           "  AND (s.status IS NULL OR UPPER(s.status) != 'CANCELLED')")
    List<BusSchedule> searchSchedulesWithRouteStops(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("travelDate") LocalDate travelDate);

    List<BusSchedule> findByBusId(Long busId);

    @Query("SELECT s FROM BusSchedule s WHERE s.busId = :busId AND (s.status IS NULL OR UPPER(s.status) != 'CANCELLED')")
    List<BusSchedule> findActiveSchedulesByBusId(@Param("busId") Long busId);

    List<BusSchedule> findByOperatorId(Long operatorId);

    @Query("SELECT s FROM BusSchedule s WHERE s.operatorId = :operatorId OR (s.operatorId IS NULL AND s.busId IN (SELECT b.id FROM Bus b WHERE b.operatorId = :operatorId)) ORDER BY s.travelDate DESC, s.departureTime ASC")
    List<BusSchedule> findByOperatorIdOrBusOperatorId(@Param("operatorId") Long operatorId);

    @Query("SELECT s FROM BusSchedule s WHERE s.busId = :busId AND s.travelDate >= :date AND (s.status IS NULL OR s.status != 'CANCELLED')")
    List<BusSchedule> findActiveUpcomingSchedulesByBusId(@Param("busId") Long busId, @Param("date") LocalDate date);

    @Query("SELECT s FROM BusSchedule s WHERE s.routeId = :routeId AND s.travelDate >= :date AND (s.status IS NULL OR UPPER(s.status) != 'CANCELLED')")
    List<BusSchedule> findActiveUpcomingSchedulesByRouteId(@Param("routeId") Long routeId, @Param("date") LocalDate date);

    long countByRouteId(Long routeId);

    List<BusSchedule> findByRouteIdOrderByTravelDateDesc(Long routeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BusSchedule s WHERE s.id = :id")
    Optional<BusSchedule> findByIdWithLock(@Param("id") Long id);
}
