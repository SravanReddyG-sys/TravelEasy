package com.traveleasy.booking.worker;

import com.traveleasy.booking.model.HotelCancellationRefundTask;
import com.traveleasy.booking.repository.HotelCancellationRefundTaskRepository;
import com.traveleasy.booking.service.HotelCancellationRefundRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Durable background worker that recovers pending, failed, or interrupted
 * refundable HOTEL cancellation refunds using the persisted snapshot refundAmount
 * and immutable idempotencyKey (WP-HC-01).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelCancellationRefundRecoveryWorker {

    private final HotelCancellationRefundTaskRepository refundTaskRepository;
    private final HotelCancellationRefundRecoveryService recoveryService;

    @Value("${booking.hotel.refund.reconciliation.enabled:true}")
    private boolean recoveryEnabled = true;

    @Value("${booking.hotel.refund.reconciliation.batch-size:10}")
    private int batchSize = 10;

    @Value("${booking.hotel.refund.reconciliation.lease-seconds:60}")
    private int leaseSeconds = 60;

    private final String workerId = "htl-refund-worker-" + UUID.randomUUID().toString().substring(0, 8);

    public void setRecoveryEnabled(boolean recoveryEnabled) {
        this.recoveryEnabled = recoveryEnabled;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setLeaseSeconds(int leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${booking.hotel.refund.reconciliation.fixed-delay:15000}")
    public void recoverPendingRefunds() {
        if (!recoveryEnabled) {
            return;
        }

        Instant now = Instant.now();
        List<HotelCancellationRefundTask> candidates = refundTaskRepository.findTasksForRetry(
                now, PageRequest.of(0, batchSize));
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        log.info("HotelCancellationRefundRecoveryWorker found {} pending/retryable hotel refund task(s)", candidates.size());
        for (HotelCancellationRefundTask task : candidates) {
            try {
                recoveryService.claimAndExecute(task.getId(), workerId, leaseSeconds);
            } catch (Exception ex) {
                log.error("Unhandled exception during hotel refund recovery for task ID {} (bookingRef {}): {}",
                        task.getId(), task.getBookingRef(), ex.getMessage(), ex);
            }
        }
    }
}
