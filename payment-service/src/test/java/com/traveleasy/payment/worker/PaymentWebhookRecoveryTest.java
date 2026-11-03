package com.traveleasy.payment.worker;

import com.traveleasy.payment.model.PaymentWebhookEvent;
import com.traveleasy.payment.repository.PaymentWebhookEventRepository;
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
public class PaymentWebhookRecoveryTest {

    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentServiceImpl paymentService;

    private PaymentWebhookRecoveryWorker recoveryWorker;

    @BeforeEach
    void setUp() {
        recoveryWorker = new PaymentWebhookRecoveryWorker(webhookEventRepository, paymentService);
        recoveryWorker.setRecoveryEnabled(true);
        recoveryWorker.setBatchSize(10);
        recoveryWorker.setLeaseSeconds(60);
        recoveryWorker.setMaxAttempts(5);
    }

    @Test
    @DisplayName("Recovery Worker: Recovers incomplete webhook event with status RECEIVED")
    void testRecoverIncompleteWebhooks_RecoversReceivedEvents() {
        PaymentWebhookEvent incompleteEvent = PaymentWebhookEvent.builder()
                .id(1L)
                .webhookEventId("evt_incomplete_received_01")
                .eventType("payment.captured")
                .status("RECEIVED")
                .payload("{\"event\":\"payment.captured\",\"payload\":{}}")
                .receivedAt(Instant.now().minusSeconds(120))
                .attemptCount(0)
                .build();

        when(webhookEventRepository.findIncompleteEvents(any(Instant.class), eq(5), any(Pageable.class)))
                .thenReturn(List.of(incompleteEvent));
        when(paymentService.recoverSingleWebhook(eq(incompleteEvent), eq(60))).thenReturn(true);

        recoveryWorker.recoverIncompleteWebhooks();

        verify(paymentService, times(1)).recoverSingleWebhook(eq(incompleteEvent), eq(60));
    }

    @Test
    @DisplayName("Recovery Worker: Recovers stale PROCESSING event after lease expiration")
    void testRecoverIncompleteWebhooks_RecoversStaleProcessingLease() {
        PaymentWebhookEvent staleProcessingEvent = PaymentWebhookEvent.builder()
                .id(2L)
                .webhookEventId("evt_stale_processing_02")
                .eventType("refund.processed")
                .status("PROCESSING")
                .lockedBy("crashed-worker-999")
                .lockedUntil(Instant.now().minusSeconds(30)) // expired lease!
                .payload("{\"event\":\"refund.processed\",\"payload\":{}}")
                .receivedAt(Instant.now().minusSeconds(300))
                .attemptCount(1)
                .build();

        when(webhookEventRepository.findIncompleteEvents(any(Instant.class), eq(5), any(Pageable.class)))
                .thenReturn(List.of(staleProcessingEvent));
        when(paymentService.recoverSingleWebhook(eq(staleProcessingEvent), eq(60))).thenReturn(true);

        recoveryWorker.recoverIncompleteWebhooks();

        verify(paymentService, times(1)).recoverSingleWebhook(eq(staleProcessingEvent), eq(60));
    }

    @Test
    @DisplayName("Recovery Worker: Skips execution when disabled")
    void testRecoverIncompleteWebhooks_Disabled_DoesNothing() {
        recoveryWorker.setRecoveryEnabled(false);
        recoveryWorker.recoverIncompleteWebhooks();

        verify(webhookEventRepository, never()).findIncompleteEvents(any(), anyInt(), any());
        verify(paymentService, never()).recoverSingleWebhook(any(), anyInt());
    }

    @Test
    @DisplayName("Recovery Worker: Handles empty incomplete events gracefully")
    void testRecoverIncompleteWebhooks_EmptyList_DoesNothing() {
        when(webhookEventRepository.findIncompleteEvents(any(Instant.class), eq(5), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        recoveryWorker.recoverIncompleteWebhooks();

        verify(paymentService, never()).recoverSingleWebhook(any(), anyInt());
    }
}
