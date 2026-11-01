package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.ScheduleFareOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleFareOverrideRepository extends JpaRepository<ScheduleFareOverride, Long> {

    List<ScheduleFareOverride> findByScheduleId(Long scheduleId);

    Optional<ScheduleFareOverride> findByScheduleIdAndFromStopOrderAndToStopOrder(Long scheduleId, Integer fromStopOrder, Integer toStopOrder);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ScheduleFareOverride o WHERE o.scheduleId = :scheduleId")
    void deleteByScheduleId(@Param("scheduleId") Long scheduleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ScheduleFareOverride o WHERE o.scheduleId = :scheduleId AND o.fromStopOrder = :fromStopOrder AND o.toStopOrder = :toStopOrder")
    void deleteByScheduleIdAndFromStopOrderAndToStopOrder(
            @Param("scheduleId") Long scheduleId,
            @Param("fromStopOrder") Integer fromStopOrder,
            @Param("toStopOrder") Integer toStopOrder);
}
