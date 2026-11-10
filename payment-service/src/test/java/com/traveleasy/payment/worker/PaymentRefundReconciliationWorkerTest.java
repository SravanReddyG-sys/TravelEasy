package com.traveleasy.payment.worker;

import com.traveleasy.payment.model.PaymentRefundTask;
import com.traveleasy.payment.repository.PaymentRefundTaskRepository;
import com.traveleasy.payment.service.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentRefundReconciliationWorkerTest {

    @Mock
    private PaymentRefundTaskRepository refundTaskRepository;

    @Mock
    private PaymentServiceImpl paymentService;

    private PaymentRefundReconciliationWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PaymentRefundReconciliationWorker(refundTaskRepository, paymentService);
        worker.setReconciliationEnabled(true);
        worker.setBatchSize(5);
        worker.setLeaseSeconds(30);
    }

    @Test
    @DisplayName("WP-HB-05: Worker skips execution when disabled")
    void testReconciliationDisabled_NoActions() {
        worker.setReconciliationEnabled(false);
        worker.reconcilePendingRefundTasks();

        verifyNoInteractions(refundTaskRepository);
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("WP-HB-05: Worker claims and executes eligible refund tasks")
    void testReconciliation_ClaimsAndExecutesTasks() {
        PaymentRefundTask task1 = PaymentRefundTask.builder()
                .id(101L)
                .bookingRef("BKG-WORKER-1")
                .transactionRef("TXN-WORKER-1")
                .status("PENDING")
                .amount(5000.0)
                .build();

        PaymentRefundTask task2 = PaymentRefundTask.builder()
                .id(102L)
                .bookingRef("BKG-WORKER-2")
                .transactionRef("TXN-WORKER-2")
                .status("FAILED_RETRYABLE")
                .amount(3000.0)
                .build();

        when(refundTaskRepository.findTasksForRetry(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(task1, task2));

        // task1 is successfully claimed; task2 is already claimed by another worker
        when(refundTaskRepository.claimRefundTask(eq(101L), anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(refundTaskRepository.claimRefundTask(eq(102L), anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(0);

        worker.reconcilePendingRefundTasks();

        // Only task1 must be executed
        verify(paymentService, times(1)).reconcileAndExecuteRefundTask(task1);
        verify(paymentService, never()).reconcileAndExecuteRefundTask(task2);
    }

    @Test
    @DisplayName("WP-HB-05: Worker catches and isolates task execution errors without stopping")
    void testReconciliation_IsolatesTaskErrors() {
        PaymentRefundTask task = PaymentRefundTask.builder()
                .id(103L)
                .bookingRef("BKG-ERR-1")
                .transactionRef("TXN-ERR-1")
                .status("PENDING")
                .amount(4000.0)
                .build();

        when(refundTaskRepository.findTasksForRetry(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(refundTaskRepository.claimRefundTask(eq(103L), anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(1);

        doThrow(new RuntimeException("Simulated worker error")).when(paymentService).reconcileAndExecuteRefundTask(task);

        // Must not throw or crash
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> worker.reconcilePendingRefundTasks());
        verify(paymentService, times(1)).reconcileAndExecuteRefundTask(task);
    }

    @Test
    @DisplayName("WP-HB-05: INITIATED tasks remain eligible for periodic reconciliation")
    void testReconciliation_InitiatedTaskRemainsEligible() {
        PaymentRefundTask initiatedTask = PaymentRefundTask.builder()
                .id(104L)
                .bookingRef("BKG-INIT-1")
                .transactionRef("TXN-INIT-1")
                .status("INITIATED")
                .razorpayRefundId("rfnd_init_104")
                .amount(2500.0)
                .attemptCount(3)
                .maxAttempts(5)
                .build();

        when(refundTaskRepository.findTasksForRetry(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(initiatedTask));
        when(refundTaskRepository.claimRefundTask(eq(104L), anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(1);

        worker.reconcilePendingRefundTasks();

        verify(paymentService, times(1)).reconcileAndExecuteRefundTask(initiatedTask);
    }
}
