package com.traveleasy.booking.repository;

import com.traveleasy.booking.model.HotelInventoryReleaseTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface HotelInventoryReleaseTaskRepository extends JpaRepository<HotelInventoryReleaseTask, Long> {

    Optional<HotelInventoryReleaseTask> findByBookingId(Long bookingId);

    Optional<HotelInventoryReleaseTask> findByBookingRef(String bookingRef);

    @Query("SELECT t FROM HotelInventoryReleaseTask t " +
           "WHERE ((t.status IN ('PENDING', 'FAILED_RETRYABLE') AND (t.nextRetryAt IS NULL OR t.nextRetryAt <= :now)) " +
           "   OR (t.status = 'PROCESSING' AND (t.lockedUntil IS NULL OR t.lockedUntil < :now))) " +
           "  AND t.attemptCount < t.maxAttempts " +
           "ORDER BY t.id ASC")
    List<HotelInventoryReleaseTask> findTasksForRetry(@Param("now") Instant now, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE HotelInventoryReleaseTask t " +
           "SET t.attemptCount = t.attemptCount + 1, " +
           "    t.status = 'PROCESSING', " +
           "    t.lockedBy = :workerId, " +
           "    t.lockedUntil = :leaseExpiry, " +
           "    t.lastAttemptAt = :now, " +
           "    t.updatedAt = :now " +
           "WHERE t.id = :id " +
           "  AND ((t.status IN ('PENDING', 'FAILED_RETRYABLE')) " +
           "   OR (t.status = 'PROCESSING' AND (t.lockedUntil IS NULL OR t.lockedUntil < :now))) " +
           "  AND t.attemptCount < t.maxAttempts")
    int claimReleaseTask(@Param("id") Long id,
                         @Param("workerId") String workerId,
                         @Param("now") Instant now,
                         @Param("leaseExpiry") Instant leaseExpiry);

    @Transactional
    @Modifying
    @Query("UPDATE HotelInventoryReleaseTask t " +
           "SET t.status = 'RELEASED', " +
           "    t.completedAt = :now, " +
           "    t.lockedBy = null, " +
           "    t.lockedUntil = null, " +
           "    t.lastError = null, " +
           "    t.updatedAt = :now " +
           "WHERE t.id = :id")
    int markReleased(@Param("id") Long id, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("UPDATE HotelInventoryReleaseTask t " +
           "SET t.status = 'FAILED_RETRYABLE', " +
           "    t.lastError = :lastError, " +
           "    t.nextRetryAt = :nextRetryAt, " +
           "    t.lockedBy = null, " +
           "    t.lockedUntil = null, " +
           "    t.updatedAt = :now " +
           "WHERE t.id = :id")
    int markFailedRetryable(@Param("id") Long id,
                            @Param("lastError") String lastError,
                            @Param("nextRetryAt") Instant nextRetryAt,
                            @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("UPDATE HotelInventoryReleaseTask t " +
           "SET t.status = 'FAILED_FINAL', " +
           "    t.lastError = :lastError, " +
           "    t.lockedBy = null, " +
           "    t.lockedUntil = null, " +
           "    t.updatedAt = :now " +
           "WHERE t.id = :id")
    int markFailedFinal(@Param("id") Long id,
                        @Param("lastError") String lastError,
                        @Param("now") Instant now);
}
