package com.traveleasy.booking.repository;

import com.traveleasy.booking.model.ScheduleCancellationBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScheduleCancellationBatchRepository extends JpaRepository<ScheduleCancellationBatch, Long> {
    Optional<ScheduleCancellationBatch> findByScheduleId(Long scheduleId);
}
