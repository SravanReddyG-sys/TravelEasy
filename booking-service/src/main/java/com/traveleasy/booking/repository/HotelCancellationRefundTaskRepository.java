package com.traveleasy.booking.repository;

import com.traveleasy.booking.model.HotelCancellationRefundTask;
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
public interface HotelCancellationRefundTaskRepository extends JpaRepository<HotelCancellationRefundTask, Long> {

    Optional<HotelCancellationRefundTask> findByBookingId(Long bookingId);

    Optional<HotelCancellationRefundTask> findByBookingRef(String bookingRef);

    Optional<HotelCancellationRefundTask> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM HotelCancellationRefundTask t " +
           "WHERE ((t.status IN ('PENDING', 'FAILED_RETRYABLE', 'INITIATED') AND (t.nextRetryAt IS NULL OR t.nextRetryAt <= :now)) " +
           "   OR (t.status = 'PROCESSING' AND (t.lockedUntil IS NULL OR t.lockedUntil < :now))) " +
           "  AND (t.status = 'INITIATED' OR t.attemptCount < t.maxAttempts) " +
           "ORDER BY t.id ASC")
    List<HotelCancellationRefundTask> findTasksForRetry(@Param("now") Instant now, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE HotelCancellationRefundTask t " +
           "SET t.attemptCount = CASE WHEN t.status = 'INITIATED' THEN t.attemptCount ELSE t.attemptCount + 1 END, " +
           "    t.status = 'PROCESSING', " +
           "    t.lockedBy = :workerId, " +
           "    t.lockedUntil = :leaseExpiry, " +
           "    t.lastAttemptAt = :now, " +
           "    t.updatedAt = :now " +
           "WHERE t.id = :id " +
           "  AND ((t.status IN ('PENDING', 'FAILED_RETRYABLE', 'INITIATED')) " +
           "   OR (t.status = 'PROCESSING' AND (t.lockedUntil IS NULL OR t.lockedUntil < :now))) " +
           "  AND (t.status = 'INITIATED' OR t.attemptCount < t.maxAttempts)")
    int claimRefundTask(@Param("id") Long id,
                        @Param("workerId") String workerId,
                        @Param("now") Instant now,
                        @Param("leaseExpiry") Instant leaseExpiry);

    @Transactional
    @Modifying
    @Query("UPDATE HotelCancellationRefundTask t " +
           "SET t.status = 'PROCESSED', " +
           "    t.razorpayRefundId = CASE WHEN :razorpayRefundId IS NOT NULL THEN :razorpayRefundId ELSE t.razorpayRefundId END, " +
           "    t.completedAt = :now, " +
           "    t.lockedBy = null, " +
           "    t.lockedUntil = null, " +
           "    t.lastError = null, " +
           "    t.updatedAt = :now " +
           "WHERE t.id = :id")
    int markProcessed(@Param("id") Long id,
                      @Param("razorpayRefundId") String razorpayRefundId,
                      @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("UPDATE HotelCancellationRefundTask t " +
           "SET t.status = 'INITIATED', " +
           "    t.razorpayRefundId = CASE WHEN :razorpayRefundId IS NOT NULL THEN :razorpayRefundId ELSE t.razorpayRefundId END, " +
           "    t.nextRetryAt = :nextRetryAt, " +
           "    t.lastError = :lastError, " +
           "    t.lockedBy = null, " +
           "    t.lockedUntil = null, " +
           "    t.updatedAt = :now " +
           "WHERE t.id = :id")
    int markInitiated(@Param("id") Long id,
                      @Param("razorpayRefundId") String razorpayRefundId,
                      @Param("lastError") String lastError,
                      @Param("nextRetryAt") Instant nextRetryAt,
                      @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("UPDATE HotelCancellationRefundTask t " +
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
    @Query("UPDATE HotelCancellationRefundTask t " +
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
