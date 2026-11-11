package com.traveleasy.payment.worker;

import com.traveleasy.payment.model.PaymentRefundTask;
import com.traveleasy.payment.repository.PaymentRefundTaskRepository;
import com.traveleasy.payment.service.PaymentServiceImpl;
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
 * Durable background worker that periodically polls for pending, failed, or timed-out
 * compensation refund tasks (WP-HB-05 Part B).
 * Guarantees at-least-once execution with lease-locking and gateway reconciliation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundReconciliationWorker {

    private final PaymentRefundTaskRepository refundTaskRepository;
    private final PaymentServiceImpl paymentService;

    @Value("${payment.refund.reconciliation.enabled:true}")
    private boolean reconciliationEnabled = true;

    @Value("${payment.refund.reconciliation.batch-size:10}")
    private int batchSize = 10;

    @Value("${payment.refund.reconciliation.lease-seconds:60}")
    private int leaseSeconds = 60;

    private final String workerId = "refund-worker-" + UUID.randomUUID().toString().substring(0, 8);

    public void setReconciliationEnabled(boolean reconciliationEnabled) {
        this.reconciliationEnabled = reconciliationEnabled;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setLeaseSeconds(int leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${payment.refund.reconciliation.fixed-delay:15000}")
    public void reconcilePendingRefundTasks() {
        if (!reconciliationEnabled) {
            return;
        }

        Instant now = Instant.now();
        List<PaymentRefundTask> retryableTasks = refundTaskRepository.findTasksForRetry(
                now, PageRequest.of(0, batchSize));

        if (retryableTasks == null || retryableTasks.isEmpty()) {
            return;
        }

        log.info("PaymentRefundReconciliationWorker found {} pending/retryable refund task(s)", retryableTasks.size());

        for (PaymentRefundTask task : retryableTasks) {
            Instant leaseExpiry = now.plusSeconds(leaseSeconds);
            int claimed = refundTaskRepository.claimRefundTask(task.getId(), workerId, now, leaseExpiry);
            if (claimed > 0) {
                log.info("Claimed refund task ID {} (bookingRef: {}) by worker {}",
                        task.getId(), task.getBookingRef(), workerId);
                try {
                    paymentService.reconcileAndExecuteRefundTask(task);
                } catch (Exception e) {
                    log.error("Unhandled exception during background refund task reconciliation for ID {}: {}",
                            task.getId(), e.getMessage(), e);
                }
            } else {
                log.debug("Refund task ID {} already leased by another worker; skipping", task.getId());
            }
        }
    }
}
