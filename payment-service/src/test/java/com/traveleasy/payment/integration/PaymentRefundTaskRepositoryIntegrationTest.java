package com.traveleasy.payment.integration;

import com.traveleasy.payment.model.PaymentRefundTask;
import com.traveleasy.payment.repository.PaymentRefundTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PaymentRefundTaskRepositoryIntegrationTest {

    @Autowired
    private PaymentRefundTaskRepository refundTaskRepository;

    private static final String BKG_PREFIX = "BKG-REPO-TEST-";

    @BeforeEach
    @AfterEach
    void cleanUp() {
        List<PaymentRefundTask> all = refundTaskRepository.findAll();
        for (PaymentRefundTask task : all) {
            if (task.getBookingRef() != null && task.getBookingRef().startsWith(BKG_PREFIX)) {
                refundTaskRepository.delete(task);
            }
        }
    }

    @Test
    @DisplayName("WP-HB-05 Repository: INITIATED tasks are selected by findTasksForRetry and can be claimed")
    void testInitiatedTask_SelectedAndClaimed() {
        Instant now = Instant.now();

        // Save an INITIATED task
        PaymentRefundTask task = PaymentRefundTask.builder()
                .bookingRef(BKG_PREFIX + "1")
                .transactionRef("TXN-REPO-1")
                .razorpayPaymentId("pay_repo_1")
                .amount(2000.0)
                .idempotencyKey("KEY-REPO-1")
                .status("INITIATED")
                .razorpayRefundId("rfnd_repo_1")
                .attemptCount(3)
                .maxAttempts(5)
                .nextRetryAt(now.minusSeconds(10))
                .build();
        PaymentRefundTask savedTask = refundTaskRepository.save(task);
        final Long taskId = savedTask.getId();

        // findTasksForRetry should find it
        List<PaymentRefundTask> retryable = refundTaskRepository.findTasksForRetry(now, PageRequest.of(0, 10));
        assertTrue(retryable.stream().anyMatch(t -> t.getId().equals(taskId)),
                "INITIATED task must be returned by findTasksForRetry");

        // claimRefundTask should succeed on INITIATED task
        Instant leaseExpiry = now.plusSeconds(60);
        int claimed = refundTaskRepository.claimRefundTask(taskId, "worker-repo-1", now, leaseExpiry);
        assertEquals(1, claimed, "INITIATED task must be successfully claimed");

        // Verify task is now PROCESSING, and attemptCount was preserved (not incremented)
        PaymentRefundTask updated = refundTaskRepository.findById(taskId).orElseThrow();
        assertEquals("PROCESSING", updated.getStatus());
        assertEquals("worker-repo-1", updated.getLockedBy());
        assertEquals(3, updated.getAttemptCount(), "attemptCount must be preserved for INITIATED status check");

        // Release initiated task
        refundTaskRepository.releaseInitiatedTask(taskId, "Transient timeout", now.plusSeconds(15), now);
        PaymentRefundTask released = refundTaskRepository.findById(taskId).orElseThrow();
        assertEquals("INITIATED", released.getStatus());
        assertNull(released.getLockedBy());
        assertNull(released.getLockedUntil());
        assertEquals("Transient timeout", released.getLastError());

        // Mark processed
        refundTaskRepository.markProcessed(taskId, "rfnd_repo_final", now);
        PaymentRefundTask processed = refundTaskRepository.findById(taskId).orElseThrow();
        assertEquals("PROCESSED", processed.getStatus());
        assertEquals("rfnd_repo_final", processed.getRazorpayRefundId());

        // Verify PROCESSED tasks are NOT selected by findTasksForRetry
        List<PaymentRefundTask> afterProcessed = refundTaskRepository.findTasksForRetry(now.plusSeconds(30), PageRequest.of(0, 10));
        assertFalse(afterProcessed.stream().anyMatch(t -> t.getId().equals(taskId)),
                "PROCESSED tasks must not be returned for retry");
    }
}
