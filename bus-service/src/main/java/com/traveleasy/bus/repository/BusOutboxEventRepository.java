package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.BusOutboxEvent;
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
public interface BusOutboxEventRepository extends JpaRepository<BusOutboxEvent, Long> {

    Optional<BusOutboxEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<BusOutboxEvent> findByLockedByAndStatus(String lockedBy, String status);

    @Transactional
    @Modifying
    @Query(value = "UPDATE bus_outbox_events " +
                   "SET status = 'IN_FLIGHT', locked_by = :workerId, locked_until = :leaseExpiry, last_attempt_at = :now " +
                   "WHERE (status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= :now)) " +
                   "   OR (status = 'IN_FLIGHT' AND (locked_until IS NULL OR locked_until < :now)) " +
                   "ORDER BY id ASC " +
                   "LIMIT :batchSize", nativeQuery = true)
    int claimPendingEvents(@Param("workerId") String workerId,
                           @Param("now") Instant now,
                           @Param("leaseExpiry") Instant leaseExpiry,
                           @Param("batchSize") int batchSize);

    @Transactional
    @Modifying
    @Query("UPDATE BusOutboxEvent e " +
           "SET e.status = :newStatus, e.publishedAt = :publishedAt, e.lockedBy = null, e.lockedUntil = null, e.lastError = null " +
           "WHERE e.id = :id AND e.status = 'IN_FLIGHT' AND e.lockedBy = :workerId")
    int markPublished(@Param("id") Long id,
                      @Param("newStatus") String newStatus,
                      @Param("publishedAt") Instant publishedAt,
                      @Param("workerId") String workerId);

    @Transactional
    @Modifying
    @Query("UPDATE BusOutboxEvent e " +
           "SET e.status = :newStatus, e.retryCount = :retryCount, e.nextRetryAt = :nextRetryAt, e.lastError = :lastError, e.lockedBy = null, e.lockedUntil = null " +
           "WHERE e.id = :id AND e.status = 'IN_FLIGHT' AND e.lockedBy = :workerId")
    int recordFailureAndUnlock(@Param("id") Long id,
                               @Param("newStatus") String newStatus,
                               @Param("retryCount") int retryCount,
                               @Param("nextRetryAt") Instant nextRetryAt,
                               @Param("lastError") String lastError,
                               @Param("workerId") String workerId);
}
