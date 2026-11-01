package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.ScheduleStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleStopRepository extends JpaRepository<ScheduleStop, Long> {

    List<ScheduleStop> findByScheduleIdOrderByStopOrderAsc(Long scheduleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ScheduleStop s WHERE s.scheduleId = :scheduleId")
    void deleteByScheduleId(@Param("scheduleId") Long scheduleId);

    Optional<ScheduleStop> findByScheduleIdAndStopOrder(Long scheduleId, Integer stopOrder);

    Optional<ScheduleStop> findByScheduleIdAndStopNameIgnoreCase(Long scheduleId, String stopName);
}
