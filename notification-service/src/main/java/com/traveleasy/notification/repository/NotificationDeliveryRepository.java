package com.traveleasy.notification.repository;

import com.traveleasy.notification.model.NotificationDelivery;
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
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    Optional<NotificationDelivery> findByDeliveryRef(String deliveryRef);

    List<NotificationDelivery> findByEventId(String eventId);

    List<NotificationDelivery> findByLockedByAndStatus(String lockedBy, String status);

    @Transactional
    @Modifying
    @Query(value = "UPDATE notification_deliveries " +
                   "SET status = 'IN_FLIGHT', locked_by = :operationId, last_attempt_at = :utcNow " +
                   "WHERE channel = 'EMAIL' " +
                   "  AND (status = 'PENDING' OR (status = 'IN_FLIGHT' AND last_attempt_at < :staleThreshold)) " +
                   "  AND (next_retry_at IS NULL OR next_retry_at <= :utcNow) " +
                   "ORDER BY id ASC " +
                   "LIMIT :batchSize", nativeQuery = true)
    int claimPendingDeliveries(@Param("operationId") String operationId,
                               @Param("utcNow") Instant utcNow,
                               @Param("staleThreshold") Instant staleThreshold,
                               @Param("batchSize") int batchSize);

    @Transactional
    @Modifying
    @Query("UPDATE NotificationDelivery d " +
           "SET d.status = :newStatus, d.providerMessageId = :providerId, d.acceptedAt = :now, d.lockedBy = null, d.lastError = null " +
           "WHERE d.id = :id AND d.status = 'IN_FLIGHT' AND d.lockedBy = :operationId")
    int finalizeSuccessfulDelivery(@Param("id") Long id,
                                   @Param("newStatus") String newStatus,
                                   @Param("providerId") String providerId,
                                   @Param("now") Instant now,
                                   @Param("operationId") String operationId);

    @Transactional
    @Modifying
    @Query("UPDATE NotificationDelivery d " +
           "SET d.status = :newStatus, d.retryCount = :retryCount, d.nextRetryAt = :nextRetryAt, d.lastError = :lastError, d.lockedBy = null " +
           "WHERE d.id = :id AND d.status = 'IN_FLIGHT' AND d.lockedBy = :operationId")
    int finalizeFailedDelivery(@Param("id") Long id,
                               @Param("newStatus") String newStatus,
                               @Param("retryCount") int retryCount,
                               @Param("nextRetryAt") Instant nextRetryAt,
                               @Param("lastError") String lastError,
                               @Param("operationId") String operationId);

    @Transactional
    int deleteByStatusAndAcceptedAtBefore(String status, Instant threshold);

    @Transactional
    int deleteByStatusAndCreatedAtBefore(String status, Instant threshold);
}
