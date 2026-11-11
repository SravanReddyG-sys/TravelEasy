package com.traveleasy.payment.worker;

import com.traveleasy.payment.model.PaymentWebhookEvent;
import com.traveleasy.payment.repository.PaymentWebhookEventRepository;
import com.traveleasy.payment.service.PaymentServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWebhookRecoveryWorker {

    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentServiceImpl paymentService;

    @Value("${payment.webhook.recovery.enabled:true}")
    private boolean recoveryEnabled = true;

    @Value("${payment.webhook.recovery.batch-size:10}")
    private int batchSize = 10;

    @Value("${payment.webhook.recovery.lease-seconds:60}")
    private int leaseSeconds = 60;

    @Value("${payment.webhook.recovery.max-attempts:5}")
    private int maxAttempts = 5;

    public void setRecoveryEnabled(boolean recoveryEnabled) {
        this.recoveryEnabled = recoveryEnabled;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setLeaseSeconds(int leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${payment.webhook.recovery.fixed-delay:30000}")
    public void recoverIncompleteWebhooks() {
        if (!recoveryEnabled) {
            return;
        }

        Instant now = Instant.now();
        List<PaymentWebhookEvent> incompleteEvents = webhookEventRepository.findIncompleteEvents(
                now, maxAttempts, PageRequest.of(0, batchSize));

        if (incompleteEvents == null || incompleteEvents.isEmpty()) {
            return;
        }

        log.info("PaymentWebhookRecoveryWorker found {} incomplete webhook event(s) for recovery", incompleteEvents.size());

        for (PaymentWebhookEvent event : incompleteEvents) {
            boolean recovered = paymentService.recoverSingleWebhook(event, leaseSeconds);
            if (recovered) {
                log.info("Recovered incomplete webhook event {}", event.getWebhookEventId());
            } else {
                log.warn("Could not recover webhook event {} (claimed by another worker or failed)", event.getWebhookEventId());
            }
        }
    }
}
