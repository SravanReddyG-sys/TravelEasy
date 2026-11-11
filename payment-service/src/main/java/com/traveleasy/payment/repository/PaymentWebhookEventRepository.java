package com.traveleasy.payment.repository;

import com.traveleasy.payment.model.PaymentWebhookEvent;
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
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    Optional<PaymentWebhookEvent> findByWebhookEventId(String webhookEventId);

    boolean existsByWebhookEventId(String webhookEventId);

    @Transactional
    @Modifying
    @Query("UPDATE PaymentWebhookEvent w " +
           "SET w.status = 'PROCESSING', w.lockedBy = :workerId, w.lockedUntil = :leaseExpiry, w.lastAttemptAt = :now, w.attemptCount = w.attemptCount + 1 " +
           "WHERE w.webhookEventId = :webhookEventId " +
           "  AND (w.status IN ('RECEIVED', 'FAILED') OR (w.status = 'PROCESSING' AND (w.lockedUntil IS NULL OR w.lockedUntil < :now)))")
    int claimWebhookEvent(@Param("webhookEventId") String webhookEventId,
                          @Param("workerId") String workerId,
                          @Param("now") Instant now,
                          @Param("leaseExpiry") Instant leaseExpiry);

    @Transactional
    @Modifying
    @Query("UPDATE PaymentWebhookEvent w " +
           "SET w.status = 'PROCESSED', w.processedAt = :processedAt, w.lockedBy = null, w.lockedUntil = null, w.lastError = null " +
           "WHERE w.webhookEventId = :webhookEventId")
    int markProcessed(@Param("webhookEventId") String webhookEventId,
                      @Param("processedAt") Instant processedAt);

    @Transactional
    @Modifying
    @Query("UPDATE PaymentWebhookEvent w " +
           "SET w.status = 'FAILED', w.lastError = :lastError, w.lockedBy = null, w.lockedUntil = null " +
           "WHERE w.webhookEventId = :webhookEventId")
    int markFailed(@Param("webhookEventId") String webhookEventId,
                   @Param("lastError") String lastError);

    @Query("SELECT w FROM PaymentWebhookEvent w " +
           "WHERE (w.status IN ('RECEIVED', 'FAILED') AND w.attemptCount < :maxAttempts) " +
           "   OR (w.status = 'PROCESSING' AND (w.lockedUntil IS NULL OR w.lockedUntil < :now) AND w.attemptCount < :maxAttempts) " +
           "ORDER BY w.id ASC")
    List<PaymentWebhookEvent> findIncompleteEvents(@Param("now") Instant now,
                                                   @Param("maxAttempts") int maxAttempts,
                                                   Pageable pageable);
}
