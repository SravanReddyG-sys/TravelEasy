package com.traveleasy.notification.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.notification.email.EmailDispatchResult;
import com.traveleasy.notification.email.NotificationEmailTemplateEngine;
import com.traveleasy.notification.email.ResendEmailClient;
import com.traveleasy.notification.model.NotificationDelivery;
import com.traveleasy.notification.repository.NotificationDeliveryRepository;
import com.traveleasy.notification.security.NotificationSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDeliveryWorker {

    private final NotificationDeliveryRepository deliveryRepository;
    private final ResendEmailClient resendEmailClient;
    private final NotificationEmailTemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 15;
    private static final int LEASE_EXPIRY_MINUTES = 5;

    @Scheduled(fixedDelayString = "${notification.delivery.fixed-delay:2000}")
    public void processPendingDeliveries() {
        String operationId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant staleThreshold = now.minus(LEASE_EXPIRY_MINUTES, ChronoUnit.MINUTES);

        // 1. Atomically Claim Candidate Records
        int claimed = claimDeliveries(operationId, now, staleThreshold);
        if (claimed == 0) {
            return;
        }

        log.debug("NotificationDeliveryWorker operation {} claimed {} pending delivery records", operationId, claimed);

        // 2. Fetch Claimed Records
        List<NotificationDelivery> claimedDeliveries = deliveryRepository.findByLockedByAndStatus(operationId, "IN_FLIGHT");

        // 3. Process Each Claimed Delivery Independently
        for (NotificationDelivery delivery : claimedDeliveries) {
            processSingleDelivery(delivery, operationId);
        }
    }

    @Transactional
    public int claimDeliveries(String operationId, Instant now, Instant staleThreshold) {
        return deliveryRepository.claimPendingDeliveries(operationId, now, staleThreshold, BATCH_SIZE);
    }

    @Transactional
    public void processSingleDelivery(NotificationDelivery delivery, String operationId) {
        try {
            Map<String, Object> templateData = new HashMap<>();
            if (delivery.getTemplateDataJson() != null && !delivery.getTemplateDataJson().isBlank()) {
                try {
                    templateData = objectMapper.readValue(delivery.getTemplateDataJson(), new TypeReference<Map<String, Object>>() {});
                } catch (Exception ex) {
                    log.warn("Failed to parse templateDataJson for deliveryRef {}: {}", delivery.getDeliveryRef(), ex.getMessage());
                }
            }

            String html = templateEngine.renderHtml(delivery.getTemplateName(), delivery.getSubject(), templateData);
            String text = templateEngine.renderPlainText(delivery.getTemplateName(), delivery.getSubject(), templateData);

            // Dispatch with deliveryRef as Idempotency-Key
            EmailDispatchResult result = resendEmailClient.sendEmail(
                    delivery.getRecipientEmail(),
                    delivery.getSubject(),
                    html,
                    text,
                    delivery.getDeliveryRef()
            );

            Instant now = Instant.now();
            if (result.isAccepted()) {
                // Distinguish explicit simulation from authentic provider acceptance
                String newStatus = result.isSimulated() ? "SIMULATED" : "ACCEPTED";
                int updated = deliveryRepository.finalizeSuccessfulDelivery(
                        delivery.getId(),
                        newStatus,
                        result.getProviderMessageId(),
                        now,
                        operationId
                );
                if (updated > 0) {
                    if (result.isSimulated()) {
                        log.info("Email delivery SIMULATED for ref {} (providerId: {})",
                                delivery.getDeliveryRef(), result.getProviderMessageId());
                    } else {
                        log.info("Email delivery ACCEPTED by provider for ref {} (providerId: {})",
                                delivery.getDeliveryRef(), result.getProviderMessageId());
                    }
                } else {
                    log.warn("Lease expired during dispatch for deliveryRef {}. Discarded local commit.", delivery.getDeliveryRef());
                }
            } else {
                // Transient vs Permanent failure classification
                boolean isTransient = result.isTransientFailure();
                int nextRetryCount = delivery.getRetryCount() + 1;
                boolean terminalFailure = !isTransient || nextRetryCount >= delivery.getMaxRetries();
                String nextStatus = terminalFailure ? "FAILED" : "PENDING";

                int backoffSeconds = 15 * (1 << nextRetryCount); // 30s, 60s, 120s, 240s
                Instant nextRetryAt = terminalFailure ? null : now.plus(backoffSeconds, ChronoUnit.SECONDS);

                int updated = deliveryRepository.finalizeFailedDelivery(
                        delivery.getId(),
                        nextStatus,
                        nextRetryCount,
                        nextRetryAt,
                        result.getErrorMessage(),
                        operationId
                );
                if (updated > 0) {
                    if (terminalFailure) {
                        if (!isTransient) {
                            log.error("Email delivery PERMANENTLY REJECTED (non-retryable) for ref {} on attempt {}. Error: {}",
                                    delivery.getDeliveryRef(), nextRetryCount, result.getErrorMessage());
                        } else {
                            log.error("Email delivery TERMINALLY FAILED for ref {} after reaching maximum {} retries. Error: {}",
                                    delivery.getDeliveryRef(), nextRetryCount, result.getErrorMessage());
                        }
                    } else {
                        log.warn("Email delivery attempt {} encountered transient error for ref {}. Scheduled retry in {}s. Error: {}",
                                nextRetryCount, delivery.getDeliveryRef(), backoffSeconds, result.getErrorMessage());
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Unexpected exception processing delivery {}: {}", delivery.getDeliveryRef(), ex.getMessage(), ex);
            deliveryRepository.finalizeFailedDelivery(
                    delivery.getId(),
                    "PENDING",
                    delivery.getRetryCount() + 1,
                    Instant.now().plus(30, ChronoUnit.SECONDS),
                    ex.getMessage(),
                    operationId
            );
        }
    }
}
