package com.traveleasy.booking.worker;

import com.traveleasy.booking.model.HotelInventoryReleaseTask;
import com.traveleasy.booking.repository.HotelInventoryReleaseTaskRepository;
import com.traveleasy.booking.service.HotelInventoryReleaseRecoveryService;
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
 * hotel room inventory releases after cancelled reservations (WP-HC-01).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelInventoryReleaseRecoveryWorker {

    private final HotelInventoryReleaseTaskRepository releaseTaskRepository;
    private final HotelInventoryReleaseRecoveryService recoveryService;

    @Value("${booking.hotel.inventory-release.reconciliation.enabled:true}")
    private boolean recoveryEnabled = true;

    @Value("${booking.hotel.inventory-release.reconciliation.batch-size:10}")
    private int batchSize = 10;

    @Value("${booking.hotel.inventory-release.reconciliation.lease-seconds:60}")
    private int leaseSeconds = 60;

    private final String workerId = "inv-release-worker-" + UUID.randomUUID().toString().substring(0, 8);

    public void setRecoveryEnabled(boolean recoveryEnabled) {
        this.recoveryEnabled = recoveryEnabled;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setLeaseSeconds(int leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${booking.hotel.inventory-release.reconciliation.fixed-delay:15000}")
    public void recoverPendingInventoryReleases() {
        if (!recoveryEnabled) {
            return;
        }

        Instant now = Instant.now();
        List<HotelInventoryReleaseTask> candidates = releaseTaskRepository.findTasksForRetry(
                now, PageRequest.of(0, batchSize));
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        log.info("HotelInventoryReleaseRecoveryWorker found {} pending/retryable inventory release task(s)", candidates.size());
        for (HotelInventoryReleaseTask task : candidates) {
            try {
                recoveryService.claimAndExecute(task.getId(), workerId, leaseSeconds);
            } catch (Exception ex) {
                log.error("Unhandled exception during hotel inventory release recovery for task ID {} (bookingRef {}): {}",
                        task.getId(), task.getBookingRef(), ex.getMessage(), ex);
            }
        }
    }
}
